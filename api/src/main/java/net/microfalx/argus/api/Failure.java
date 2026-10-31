package net.microfalx.argus.api;

import lombok.Getter;
import lombok.ToString;
import lombok.With;
import lombok.extern.slf4j.Slf4j;
import net.microfalx.lang.*;
import net.microfalx.metrics.Metrics;
import net.microfalx.metrics.statistics.MutableStatisticalSummary;
import net.microfalx.metrics.statistics.TimeWindowStatisticalSummary;
import net.microfalx.metrics.statistics.TrendStatisticalSummary;

import javax.naming.NameNotFoundException;
import javax.naming.ServiceUnavailableException;
import javax.naming.directory.NoSuchAttributeException;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.*;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarException;
import java.util.zip.DataFormatException;
import java.util.zip.ZipException;

import static net.microfalx.argus.api.Failure.Type.INTERNAL_ERROR;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.CollectionUtils.immutableMap;

/**
 * A class which represents a failure description in the application. It contains information about the type of failure and the associated exception (if any).
 * This class can be used to encapsulate failure information and provide a standardized way to handle failures across the application.
 */
@SuppressWarnings("LombokGetterMayBeUsed")
@Slf4j
@Getter
@ToString
public final class Failure {

    private static final Map<Class<? extends Throwable>, Type> exceptionTypes = new ConcurrentHashMap<>();
    private static final Map<Class<? extends Throwable>, Type> exceptionSubTypes = new ConcurrentHashMap<>();
    private static final Collection<TypeExtractor> extractors = new CopyOnWriteArrayList<>();
    private static final Metrics METRICS = Metrics.of("Failure");
    private static final Metrics MISSING = METRICS.withGroup("Missing");
    private static final Metrics MAPPED = METRICS.withGroup("Mapped");
    private static final Metrics FAILURE_BY_EXCEPTION = METRICS.withGroup("Exception");
    private static final Metrics FAILURE_BY_EXCEPTION_WARN = FAILURE_BY_EXCEPTION.withGroup("Warn");
    private static final Metrics FAILURE_BY_EXCEPTION_ERROR = FAILURE_BY_EXCEPTION.withGroup("Error");
    private static final Metrics FAILURE_BY_TYPE = METRICS.withGroup("Type");
    private static final Metrics FAILURE_BY_TYPE_WARN = FAILURE_BY_TYPE.withGroup("Warn");
    private static final Metrics FAILURE_BY_TYPE_ERROR = FAILURE_BY_TYPE.withGroup("Error");

    private static final Map<Class<? extends Throwable>, Counts> exceptionCounts = new ConcurrentHashMap<>();
    private static final Map<Type, Counts> typeCounts = new ConcurrentHashMap<>();

    private static final Map<Type, TrendStatisticalSummary> typeWarningsTrend = new ConcurrentHashMap<>();
    private static final Map<Type, TrendStatisticalSummary> typeErrorsTrend = new ConcurrentHashMap<>();

    /**
     * The type of failure, which categorizes the nature of the failure (e.g., connectivity issues, authentication failures, etc).
     */
    private final Type type;

    /**
     * The exception associated with the failure, which provides more details about the failure. This can be null if there is no specific exception associated with the failure.
     */
    private final Throwable throwable;

    /**
     * Holds the severity of the failure, which indicates how critical the failure is (e.g., warning, error).
     * This can be used to determine how to handle the failure in the application.
     */
    @With
    private final Severity severity;

    /**
     * Returns the failure description by looking into the call stack. It tries to extract <code>Failure.Type</code> using custom
     *
     * @param throwable the exception to examine
     * @return a non-null instance
     */
    public static Failure of(Throwable throwable) {
        if (throwable == null) return new Failure(INTERNAL_ERROR, null, Severity.ERROR);
        return new Failure(getType(throwable), throwable, Severity.ERROR);
    }

    private Failure(Type type, Throwable throwable, Severity severity) {
        this.type = type;
        this.throwable = throwable;
        this.severity = severity;
    }

    /**
     * Returns a friendly name of the root cause exception. If there is no exception, it returns "N/A".
     *
     * @return a non-null instance
     * @see ExceptionUtils#getRootCauseName(Throwable)
     */
    public String getRootCauseName() {
        if (throwable == null) return StringUtils.NA_STRING;
        return ExceptionUtils.getRootCauseName(throwable);
    }

    /**
     * Returns the root cause message of the associated exception. If there is no exception, it returns "N/A".
     *
     * @return a non-null instance
     * @see ExceptionUtils#getRootCauseMessage(Throwable)
     */
    public String getRootCauseMessage() {
        if (throwable == null) return StringUtils.NA_STRING;
        return ExceptionUtils.getRootCauseMessage(throwable);
    }

    /**
     * Returns the root cause description.
     * <p>
     * The root cause description has the root cause message and the type of failure in parentheses.
     *
     * @return the description
     */
    public String getRootCauseDescription() {
        if (throwable == null) return StringUtils.NA_STRING;
        return ExceptionUtils.getRootCauseDescription(throwable);
    }

    /**
     * Returns whether the failure or any of its causes is of the given type.
     *
     * @param exceptionClass the exception class
     * @return {@code true} if the exception or any of its causes is of the given type, {@code false} otherwise
     * @see ExceptionUtils#contains(Throwable, Class)
     */
    public boolean contains(Class<? extends Throwable> exceptionClass) {
        if (throwable == null) return false;
        return ExceptionUtils.contains(throwable, exceptionClass);
    }

    /**
     * Returns whether the failure or any of its causes is any of the given types.
     *
     * @param exceptionClasses the exception classes
     * @return {@code true} if the exception or any of its causes is of the given types, {@code false} otherwise
     * @see ExceptionUtils#contains(Throwable, Class)
     */
    @SafeVarargs
    public final boolean contains(Class<? extends Throwable>... exceptionClasses) {
        if (throwable == null) return false;
        for (Class<? extends Throwable> exceptionClass : exceptionClasses) {
            if (ExceptionUtils.contains(throwable, exceptionClass)) return true;
        }
        return false;
    }

    /**
     * Registers the failure count.
     */
    public void register() {
        Throwable rootThrowable = ExceptionUtils.getRootCause(throwable);
        Class<? extends Throwable> exceptionClass = rootThrowable != null ? rootThrowable.getClass() : null;
        String exceptionClassName = rootThrowable != null ? ClassUtils.getCompactName(rootThrowable) : StringUtils.NA_STRING;
        String typeName = StringUtils.capitalizeWords(type.name());
        if (severity == Severity.WARNING) {
            getCounts(type).incrementWarningCount();
            if (exceptionClass != null) {
                getCounts(exceptionClass).incrementWarningCount();
            }
            FAILURE_BY_TYPE_WARN.count(typeName);
            FAILURE_BY_EXCEPTION_WARN.count(exceptionClassName);
            increment(typeWarningsTrend.computeIfAbsent(type, key -> new TimeWindowStatisticalSummary()));
        } else {
            getCounts(type).incrementErrorCount();
            if (exceptionClass != null) {
                getCounts(exceptionClass).incrementErrorCount();
            }
            FAILURE_BY_TYPE_ERROR.count(typeName);
            FAILURE_BY_EXCEPTION_ERROR.count(exceptionClassName);
            increment(typeErrorsTrend.computeIfAbsent(type, key -> new TimeWindowStatisticalSummary()));
        }
    }

    /**
     * Returns warning trends grouped by failure type.
     */
    public static Map<Type, TrendStatisticalSummary> getTypeWarningsTrend() {
        return immutableMap(typeWarningsTrend);
    }

    /**
     * Returns error trends grouped by failure type.
     */
    public static Map<Type, TrendStatisticalSummary> getTypeErrorsTrend() {
        return immutableMap(typeErrorsTrend);
    }

    /**
     * Returns warning counters grouped by exception class.
     */
    public static Map<Class<? extends Throwable>, Counts> getExceptionCounts() {
        return immutableMap(exceptionCounts);
    }

    /**
     * Returns warning counters grouped by failure type.
     */
    public static Map<Type, Counts> getTypeCounts() {
        return immutableMap(typeCounts);
    }

    /**
     * Registers a failure type associated with a specific exception.
     *
     * @param type           exception type
     * @param exceptionClass exception class
     */
    public static void registerType(Failure.Type type, Class<? extends Throwable> exceptionClass) {
        requireNonNull(type);
        requireNonNull(exceptionClass);
        LOGGER.debug("Register failure type {} for {}", type, ClassUtils.getName(exceptionClass));
        exceptionTypes.put(exceptionClass, type);
    }

    /**
     * Registers a failure type extractor.
     *
     * @param extractor the extractor
     */
    public static void registerExtractor(TypeExtractor extractor) {
        requireNonNull(extractor);
        LOGGER.debug("Register failure type extractor {}", ClassUtils.getName(extractor));
        extractors.add(extractor);
    }

    /**
     * Registers a failure type associated with all subclasses of a give exception.
     *
     * @param type           exception type
     * @param exceptionClass exception class
     */
    public static void registerSubType(Failure.Type type, Class<? extends Throwable> exceptionClass) {
        requireNonNull(type);
        requireNonNull(exceptionClass);
        LOGGER.debug("Register failure sub type {} for {}", type, ClassUtils.getName(exceptionClass));
        exceptionSubTypes.put(exceptionClass, type);
    }

    /**
     * Returns the failure type by looking into the call stack. It tries to extract <code>Failure.Type</code> using custom
     * exception type extractors, and then it falls back to per exception class exception type.
     *
     * @param throwable an exception
     * @return the failure description
     */
    public static Failure.Type getType(Throwable throwable) {
        if (throwable == null) return INTERNAL_ERROR;
        List<Throwable> throwableList = org.apache.commons.lang3.exception.ExceptionUtils.getThrowableList(throwable);
        Collections.reverse(throwableList);
        Failure.Type type = getType(throwableList, false);
        if (type == null) type = getType(throwableList, true);
        if (type == null) {
            type = INTERNAL_ERROR;
            registerMissing(throwable);
        }
        return type;
    }

    private static Failure.Type getType(Collection<Throwable> throwableList, boolean deferred) {
        for (Throwable throwable : throwableList) {
            Throwable unwrapThrowable = unwrapThrowable(throwable);
            Class<? extends Throwable> unwrapThrowableClass = unwrapThrowable.getClass();
            Failure.Type type = exceptionTypes.get(unwrapThrowableClass);
            if (type != null && type.isDeferred() == deferred && type != INTERNAL_ERROR) {
                return registerMapped(type);
            }
            type = resolveType(unwrapThrowable);
            if (type != null && type.isDeferred() == deferred && type != INTERNAL_ERROR) {
                return registerMapped(type);
            }
        }
        return null;
    }

    /**
     * Returns the real exception by unwrapping a few known JVM wrappers.
     *
     * @param throwable the exception to examine
     * @return The root cause
     */
    public static Throwable unwrapThrowable(Throwable throwable) {
        if (throwable == null) return null;
        Throwable cause = throwable;
        int iterationCount = 50;
        while (iterationCount-- > 0) {
            if (cause instanceof InvocationTargetException) {
                cause = ((InvocationTargetException) cause).getTargetException();
            } else if (cause instanceof UndeclaredThrowableException) {
                cause = ((UndeclaredThrowableException) cause).getUndeclaredThrowable();
            } else {
                return cause;
            }
        }
        return cause;
    }

    public static void reset() {
        exceptionTypes.clear();
        exceptionSubTypes.clear();
        registerFailureTypes();
    }

    private static Failure.Type resolveType(Throwable throwable) {
        for (Map.Entry<Class<? extends Throwable>, Type> entry : exceptionSubTypes.entrySet()) {
            if (ClassUtils.isSubClassOf(throwable.getClass(), entry.getKey())) return entry.getValue();
        }
        for (TypeExtractor extractor : extractors) {
            Type type = extractor.resolve(throwable);
            if (type != null) return type;
        }
        return null;
    }

    private static void increment(Number value) {
        ((AtomicInteger) value).incrementAndGet();
    }

    private static void increment(TrendStatisticalSummary value) {
        ((MutableStatisticalSummary) value).add(1);
    }

    private static Counts getCounts(Class<? extends Throwable> exceptionClass) {
        return exceptionCounts.computeIfAbsent(exceptionClass, k -> new Counts(exceptionClass));
    }

    private static Counts getCounts(Type type) {
        return typeCounts.computeIfAbsent(type, k -> new Counts(type));
    }

    private static void registerMissing(Throwable throwable) {
        MISSING.count(ClassUtils.getName(throwable));
    }

    private static Failure.Type registerMapped(Failure.Type type) {
        MAPPED.count(type.name());
        return type;
    }

    private static void registerExtractors() {
        registerExtractor(new IOExceptionTypeExtractor());
    }

    private static void registerFailureTypes() {
        registerType(Type.CONNECTIVITY, java.net.ConnectException.class);
        registerType(Type.CONNECTIVITY, ClosedChannelException.class);
        registerType(Type.CONNECTIVITY, ClosedByInterruptException.class);
        registerType(Type.CONNECTIVITY, javax.naming.CommunicationException.class);
        registerType(Type.CONNECTIVITY, java.rmi.ConnectException.class);
        registerType(Type.CONNECTIVITY, java.net.NoRouteToHostException.class);
        registerType(Type.CONNECTIVITY, UnknownHostException.class);
        registerType(Type.CONNECTIVITY, BindException.class);
        registerType(Type.CONNECTIVITY, NoRouteToHostException.class);
        registerType(Type.CONNECTIVITY, UnknownServiceException.class);
        registerType(Type.CONNECTIVITY, PortUnreachableException.class);

        registerType(Type.TIMED_OUT, java.util.concurrent.TimeoutException.class);
        registerType(Type.TIMED_OUT, java.net.SocketTimeoutException.class);

        registerType(Type.NETWORK, InterruptedIOException.class);
        registerType(Type.NETWORK, SocketException.class);
        registerSubType(Type.NETWORK, IOException.class);

        registerType(Type.RESOURCE_NOT_FOUND, FileNotFoundException.class);
        registerType(Type.RESOURCE_NOT_FOUND, NoSuchFileException.class);
        registerType(Type.RESOURCE_NOT_FOUND, NoSuchAttributeException.class);
        registerType(Type.RESOURCE_NOT_FOUND, NameNotFoundException.class);

        registerType(Type.AUTHORIZATION, AccessDeniedException.class);
        registerType(Type.AUTHORIZATION, SecurityException.class);

        registerType(Type.CORRUPTED_DATA, DataFormatException.class);
        registerType(Type.CORRUPTED_DATA, ZipException.class);
        registerType(Type.CORRUPTED_DATA, JarException.class);
        registerType(Type.CORRUPTED_DATA, EOFException.class);

        registerType(Type.ILLEGAL_INPUT, URISyntaxException.class);
        registerType(Type.ILLEGAL_INPUT, IllegalArgumentException.class);
        registerType(Type.ILLEGAL_INPUT, IndexOutOfBoundsException.class);
        registerType(Type.ILLEGAL_INPUT, ArrayIndexOutOfBoundsException.class);

        registerType(Type.SERVICE_UNAVAILABLE, ServiceUnavailableException.class);

        registerType(INTERNAL_ERROR, NullPointerException.class);
    }

    static {
        registerFailureTypes();
        registerExtractors();
    }

    /**
     * An interface for extracting the failure type from an exception. This allows for custom logic to determine the
     * failure type based on the exception, which can be useful
     */
    public interface TypeExtractor {

        /**
         * Extracts the failure type from the given exception. This method should return a non-null failure type if it can determine the type of failure
         * based on the exception, or null if it cannot determine the type.
         *
         * @param throwable the exception to examine
         * @return the failure type, or null if it cannot be determined
         */
        Type resolve(Throwable throwable);
    }

    /**
     * A severity associated with the failure.
     */
    public enum Severity {

        /**
         * A warning indicates a potential issue that should be looked into, but it does not necessarily indicate a failure.
         */
        WARNING,

        /**
         * An error indicates a failure that needs to be addressed. It represents a significant problem that has occurred in the application.
         */
        ERROR
    }

    /**
     * An enum which defines the type of failure. This is used to categorize failures and provide more context about the nature
     * of the failure. The specific types of failures can be defined based on the application's requirements, such as validation
     * errors, authentication failures, authorization failures, etc.
     */
    public enum Type {

        /**
         * A category for network accessibility failures (a service cannot be reached, etc).
         */
        CONNECTIVITY(true, true, true),

        /**
         * A category for network-related failures that are not necessarily connectivity issues, such as I/O exceptions,
         * socket exceptions, etc.
         */
        NETWORK(true, true, true),

        /**
         * A category for network/service timeout failures (it can be reached, but it doesn't respond in a timely manner).
         */
        TIMED_OUT(true, true, true),

        /**
         * A category for authentication failures (wrong credentials, cannot perform authentication, etc).
         */
        AUTHENTICATION(false, false, false),

        /**
         * A category for authorization failures (access denied, not enough privileges, etc).
         */
        AUTHORIZATION(false, false, false),

        /**
         * A category for service availability.
         */
        SERVICE_UNAVAILABLE(true, true, false),

        /**
         * A category for system capacity limits (too many requests in a given amount of time). Intended for use with rate limiting schemes.
         */
        OVERLOAD(true, true, false),

        /**
         * A category for missing resources (file, etc).
         */
        RESOURCE_NOT_FOUND(false, false, false),

        /**
         * A category for temporary unavailability of an existing resource due to resource being busy (used by another process).
         */
        RESOURCE_BUSY(true, true, false),

        /**
         * A category for temporary unavailability of a resource due to unknown causes.
         */
        RESOURCE_UNAVAILABLE(true, true, false),

        /**
         * Data access exceptions that are considered transient - where a previously failed operation might be able to succeed when the operation
         * is retried without any intervention by application-level functionality.
         */
        TRANSIENT_DATA_ACCESS(true, true, false),

        /**
         * Data access exceptions that are considered non-transient - where a retry of the same operation would fail unless the cause of the Exception
         * is corrected.
         */
        NON_TRANSIENT_DATA_ACCESS(false, false, false),

        /**
         * A category for all data corruption failures (cannot open archives & other formats, etc).
         */
        CORRUPTED_DATA(false, false, false),

        /**
         * A category for failures due to storage unavailability (file system, database storage, memory storage, etc).
         */
        INSUFFICIENT_STORAGE(true, true, false),

        /**
         * A category for failures due to application misconfiguration.
         */
        CONFIGURATION(false, false, false),

        /**
         * A category for failures due to invalid input when performing an action. It is different from <code>CORRUPTED_DATA</code> because it doesn't
         * represent data processing, just performing an action on invalid inputs. Failures could be:
         * <ul>
         * <li>failure to create an URI/URL</li>
         * <li>failure to invoke a database procedure due to illegal arguments</li>
         * <li>failure to invoke services with appropriate arguments</li>
         * </ul>
         * This failure is not recoverable without manual intervention (correcting a configuration, patching the application, etc).
         */
        ILLEGAL_INPUT(false, false, false),

        /**
         * A category for failures due to an invalid output when performing an action. This failure is not recoverable
         * without manual intervention (correcting a configuration, patching the application, etc).
         */
        ILLEGAL_OUTPUT(false, false, false),

        /**
         * A category for failures due to conflict in the request, such as an edit conflict (version conflict) or a constraint violation.
         */
        CONFLICT(false, true, false),

        /**
         * A category for "failures" which should not considered failures and they should be treated as
         * "abort/cancel current operation" and report no failure.
         */
        ABORT(false, false, false),

        /**
         * A category for "failures" which should not considered failures and they should be treated as
         * "client asks for a reset" and retried.
         */
        RESET(true, true, false),

        /**
         * A category for any other types of failures (usually bugs).
         */
        INTERNAL_ERROR(false, false, false);

        private final boolean _transient;
        private final boolean retriable;
        private final boolean deferred;

        Type(boolean _transient, boolean retriable, boolean deferred) {
            this._transient = _transient;
            this.retriable = retriable;
            this.deferred = deferred;
            if (_transient && !retriable) {
                throw new IllegalArgumentException("A transient failure should be retriable, name " + name());
            }
        }

        /**
         * Returns if the exception type indicates a failure that should be retried (using a retry policy).
         *
         * @return {@code true} if the failure should be retried, {@code false} otherwise
         */
        public boolean isRetriable() {
            return retriable;
        }

        /**
         * Returns whether the exception type indicates a failure that is transient, meaning that it might be resolved
         * without any intervention by application-level functionality, and that a retry might be successful.
         *
         * @return {@code true} if the failure is transient, {@code false} otherwise
         */
        public boolean isTransient() {
            return _transient;
        }

        /**
         * Returns whether this type of failure should be deferred after other failure types were resolved first.
         *
         * @return {@code true} if the failure should be deferred, {@code false} otherwise
         */
        public boolean isDeferred() {
            return deferred;
        }

        /**
         * Returns whether the exception type indicates a failure that is related to network issues, such as connectivity
         * problems, timeouts, or other network-related errors.
         *
         * @return {@code true} if the failure is related to network issues, {@code false} otherwise
         */
        public boolean isNetwork() {
            return this == CONNECTIVITY || this == TIMED_OUT;
        }

        /**
         * Returns whether the exception type indicates a failure that is related to security issues,
         * such as authentication or authorization failures.
         *
         * @return {@code true} if the failure is related to security issues, {@code false} otherwise
         */
        public boolean isSecurity() {
            return this == AUTHENTICATION || this == AUTHORIZATION;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", Type.class.getSimpleName() + "[", "]")
                    .add("name=" + name())
                    .add("retriable=" + retriable)
                    .add("_transient=" + _transient)
                    .toString();
        }
    }

    @Getter
    @ToString(callSuper = true)
    public static class Counts extends NamedIdentityAware<String> {

        private volatile int warningCount;
        private volatile int errorCount;

        Counts(Failure.Type type) {
            setId(type.name());
            setName(EnumUtils.toLabel(type));
        }

        Counts(Class<?> exceptionType) {
            setId(exceptionType.getName());
            setName(ClassUtils.getName(exceptionType));
        }

        private synchronized void incrementWarningCount() {
            warningCount++;
        }

        private synchronized void incrementErrorCount() {
            errorCount++;
        }

    }

    static class IOExceptionTypeExtractor implements TypeExtractor {

        private static final String[] NOT_ENOUGH_SPACE_MESSAGES = new String[]{
                "There is not enough space on the disk",
                "Not enough space",
                "No space left on device",
        };

        private static final String[] RESET_MESSAGES = new String[]{
                "Connection reset by peer"
        };

        private static final String[] COMMUNICATION_MESSAGES = new String[]{

        };

        @Override
        public Type resolve(Throwable throwable) {
            if (!(throwable instanceof IOException ioException)) return null;
            String message = ioException.getMessage();
            if (message == null) return null;
            if (ObjectUtils.contains(message, NOT_ENOUGH_SPACE_MESSAGES)) return Type.INSUFFICIENT_STORAGE;
            if (ObjectUtils.contains(message, COMMUNICATION_MESSAGES)) return Type.NETWORK;
            if (ObjectUtils.contains(message, RESET_MESSAGES)) return Type.RESET;
            return null;
        }
    }
}
