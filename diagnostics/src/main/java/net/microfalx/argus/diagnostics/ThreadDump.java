package net.microfalx.argus.diagnostics;

import com.sun.management.HotSpotDiagnosticMXBean;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import net.microfalx.lang.ExceptionUtils;
import net.microfalx.resource.Resource;

import java.io.File;
import java.io.IOException;
import java.lang.management.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;

/**
 * A thread dump of a JVM.
 * <p>
 * The dump is produced in one of the following formats (see {@link Format}):
 * <ul>
 *     <li>{@link Format#JSTACK} (default): the {@code Thread.print} diagnostic command, invoked through the
 *     diagnostic command MBean (see {@link DiagnosticCommands}). This produces the same output as {@code jstack} /
 *     {@code jcmd Thread.print} (or a {@code SIGQUIT}): thread names, priorities, native thread identifiers,
 *     CPU and elapsed time, states, full stack traces, monitors, locked synchronizers and deadlock detection.
 *     Virtual threads are not included. If the diagnostic command MBean is not available (or it fails), the dump
 *     is generated from the {@link ThreadMXBean} and formatted to resemble the output of {@code jstack}.</li>
 *     <li>{@link Format#TEXT} and {@link Format#JSON}: the HotSpot diagnostic MBean
 *     ({@link HotSpotDiagnosticMXBean#dumpThreads(String, HotSpotDiagnosticMXBean.ThreadDumpFormat)}, the
 *     equivalent of {@code jcmd Thread.dump_to_file}). These include virtual threads (grouped by their
 *     executor / container), but no lock information.</li>
 * </ul>
 */
@Slf4j
@ToString(callSuper = true)
public class ThreadDump extends Diagnostics {

    private static final String THREAD_PRINT_OPERATION = "threadPrint";
    private static final String LOCKED_SYNCHRONIZERS_ARGUMENT = "-l";

    private static final DateTimeFormatter HEADER_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ThreadDump(String name) {
        this(name, Options.create());
    }

    public ThreadDump(String name, Options options) {
        super(name, options);
        options.validate();
    }

    @Override
    public Options getOptions() {
        return (Options) super.getOptions();
    }

    @Override
    protected Type getType() {
        return Type.THREAD_DUMP;
    }

    @Override
    protected void collect(Resource directory) throws Exception {
        Options options = getOptions();
        File file = getFile(directory, options.getFormat().getExtension());
        if (options.getFormat() == Format.JSTACK) {
            String dump = extract(options.isLockedSynchronizers());
            Files.writeString(file.toPath(), dump, StandardCharsets.UTF_8);
        } else {
            dumpThreads(file, options.getFormat());
        }
        updateResource(Resource.file(file));
    }

    private String extract(boolean lockedSynchronizers) {
        try {
            return extractWithDiagnosticCommand(lockedSynchronizers);
        } catch (Exception e) {
            LOGGER.warn("Failed to extract thread dump '{}' with the diagnostic command MBean, fallback to the thread MBean, root cause: {}",
                    getName(), ExceptionUtils.getRootCauseDescription(e));
            try {
                return extractWithThreadBean(lockedSynchronizers);
            } catch (Exception te) {
                te.addSuppressed(e);
                throw te;
            }
        }
    }

    /**
     * Writes a thread dump (which includes virtual threads) with the HotSpot diagnostic MBean.
     *
     * @param file   the file where the dump is written
     * @param format the format, {@link Format#TEXT} or {@link Format#JSON}
     */
    void dumpThreads(File file, Format format) throws IOException {
        requireNonNull(file);
        HotSpotDiagnosticMXBean.ThreadDumpFormat dumpFormat = switch (format) {
            case TEXT -> HotSpotDiagnosticMXBean.ThreadDumpFormat.TEXT_PLAIN;
            case JSON -> HotSpotDiagnosticMXBean.ThreadDumpFormat.JSON;
            case JSTACK -> throw new IllegalArgumentException("Format " + format + " is not supported by the HotSpot diagnostic MBean");
        };
        HotSpotDiagnosticMXBean bean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        if (bean == null) {
            throw new DiagnosticsException("HotSpot diagnostic MBean is not available, thread dumps in " + format
                    + " format are not supported");
        }
        // the MBean refuses to overwrite an existing file
        Files.deleteIfExists(file.toPath());
        try {
            bean.dumpThreads(file.getAbsolutePath(), dumpFormat);
        } catch (IOException | RuntimeException e) {
            throw new DiagnosticsException("Failed to create thread dump '" + getName() + "' in " + file, e);
        }
    }

    /**
     * Extracts the thread dump with the {@code Thread.print} diagnostic command, exported over JMX.
     *
     * @param lockedSynchronizers {@code true} to include the locked ownable synchronizers ({@code -l})
     * @return the thread dump
     */
    String extractWithDiagnosticCommand(boolean lockedSynchronizers) {
        String[] arguments = lockedSynchronizers ? new String[]{LOCKED_SYNCHRONIZERS_ARGUMENT} : new String[0];
        return DiagnosticCommands.execute(THREAD_PRINT_OPERATION, arguments);
    }

    /**
     * Extracts the thread dump with the {@link ThreadMXBean}, formatted like {@code jstack}.
     * <p>
     * Unlike {@link ThreadInfo#toString()}, the stack traces are not truncated.
     *
     * @param lockedSynchronizers {@code true} to include the locked ownable synchronizers
     * @return the thread dump
     */
    String extractWithThreadBean(boolean lockedSynchronizers) {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        boolean monitors = bean.isObjectMonitorUsageSupported();
        boolean synchronizers = lockedSynchronizers && bean.isSynchronizerUsageSupported();
        ThreadInfo[] threads = bean.dumpAllThreads(monitors, synchronizers);
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        StringBuilder builder = new StringBuilder(threads.length * 1024);
        builder.append(HEADER_TIMESTAMP_FORMAT.format(LocalDateTime.now())).append('\n');
        builder.append("Full thread dump ").append(runtime.getVmName())
                .append(" (").append(runtime.getVmVersion()).append("):\n\n");
        for (ThreadInfo thread : threads) {
            if (thread != null) appendThread(builder, thread, synchronizers);
        }
        appendDeadlocks(builder, bean, synchronizers);
        return builder.toString();
    }

    private static void appendThread(StringBuilder builder, ThreadInfo thread, boolean synchronizers) {
        builder.append('"').append(thread.getThreadName()).append("\" #").append(thread.getThreadId());
        if (thread.isDaemon()) builder.append(" daemon");
        builder.append(" prio=").append(thread.getPriority()).append('\n');
        builder.append("   java.lang.Thread.State: ").append(thread.getThreadState());
        if (thread.isSuspended()) builder.append(" (suspended)");
        if (thread.isInNative()) builder.append(" (in native)");
        builder.append('\n');
        StackTraceElement[] stackTrace = thread.getStackTrace();
        MonitorInfo[] monitors = thread.getLockedMonitors();
        for (int depth = 0; depth < stackTrace.length; depth++) {
            builder.append("\tat ").append(stackTrace[depth]).append('\n');
            if (depth == 0) appendWaitingOn(builder, thread);
            for (MonitorInfo monitor : monitors) {
                if (monitor.getLockedStackDepth() == depth) {
                    builder.append("\t- locked ");
                    appendLock(builder, monitor);
                    builder.append('\n');
                }
            }
        }
        if (synchronizers) {
            builder.append("\n   Locked ownable synchronizers:\n");
            LockInfo[] locks = thread.getLockedSynchronizers();
            if (locks.length == 0) {
                builder.append("\t- None\n");
            } else {
                for (LockInfo lock : locks) {
                    builder.append("\t- ");
                    appendLock(builder, lock);
                    builder.append('\n');
                }
            }
        }
        builder.append('\n');
    }

    private static void appendWaitingOn(StringBuilder builder, ThreadInfo thread) {
        LockInfo lock = thread.getLockInfo();
        if (lock == null) return;
        String action = switch (thread.getThreadState()) {
            case BLOCKED -> "waiting to lock ";
            case WAITING, TIMED_WAITING -> isParked(thread) ? "parking to wait for  " : "waiting on ";
            default -> null;
        };
        if (action == null) return;
        builder.append("\t- ").append(action);
        appendLock(builder, lock);
        if (thread.getLockOwnerName() != null) {
            builder.append(" owned by \"").append(thread.getLockOwnerName()).append("\" #").append(thread.getLockOwnerId());
        }
        builder.append('\n');
    }

    private static boolean isParked(ThreadInfo thread) {
        StackTraceElement[] stackTrace = thread.getStackTrace();
        return stackTrace.length > 0 && "park".equals(stackTrace[0].getMethodName());
    }

    private static void appendLock(StringBuilder builder, LockInfo lock) {
        builder.append("<0x").append(String.format("%016x", lock.getIdentityHashCode())).append("> (a ")
                .append(lock.getClassName()).append(')');
    }

    private static void appendDeadlocks(StringBuilder builder, ThreadMXBean bean, boolean synchronizers) {
        long[] deadlocked = synchronizers ? bean.findDeadlockedThreads() : bean.findMonitorDeadlockedThreads();
        if (deadlocked == null || deadlocked.length == 0) return;
        builder.append("Found ").append(deadlocked.length == 1 ? "one" : deadlocked.length)
                .append(" Java-level deadlock:\n=============================\n");
        for (ThreadInfo thread : bean.getThreadInfo(deadlocked, true, synchronizers)) {
            if (thread == null) continue;
            builder.append('"').append(thread.getThreadName()).append("\":\n  waiting to lock ");
            if (thread.getLockInfo() != null) appendLock(builder, thread.getLockInfo());
            builder.append(",\n  which is held by \"").append(thread.getLockOwnerName()).append("\"\n");
        }
        builder.append('\n');
    }

    /**
     * Options for thread dumps.
     */
    @Getter
    @ToString(callSuper = true)
    @SuperBuilder
    public static class Options extends Diagnostics.Options {

        /**
         * The format of the thread dump.
         */
        @lombok.Builder.Default
        private final Format format = Format.JSTACK;

        /**
         * Indicates whether the locked ownable synchronizers (like {@link java.util.concurrent.locks.ReentrantLock})
         * held by each thread are included, like {@code jstack -l}. Used only by {@link Format#JSTACK}.
         * <p>
         * This requires a scan of the heap, which can be expensive for large heaps.
         */
        @lombok.Builder.Default
        private final boolean lockedSynchronizers = true;

        /**
         * Returns the default options.
         *
         * @return a non-null instance
         */
        public static Options create() {
            return builder().build();
        }

        void validate() {
            requireNonNull(format);
        }
    }

    /**
     * The formats of a thread dump.
     */
    @Getter
    public enum Format {

        /**
         * The format produced by {@code jstack} / {@code jcmd Thread.print}, with lock information and deadlock
         * detection; virtual threads are not included.
         */
        JSTACK("txt", false),

        /**
         * Plain text, includes virtual threads, without lock information.
         */
        TEXT("txt", true),

        /**
         * JSON, includes virtual threads (grouped by their executor / container), without lock information.
         */
        JSON("json", true);

        private final String extension;
        private final boolean virtualThreads;

        Format(String extension, boolean virtualThreads) {
            this.extension = extension;
            this.virtualThreads = virtualThreads;
        }
    }
}
