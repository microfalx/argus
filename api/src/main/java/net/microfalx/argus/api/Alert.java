package net.microfalx.argus.api;

import lombok.*;
import net.microfalx.lang.*;

import java.time.LocalDateTime;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ExceptionUtils.getRootCauseName;
import static net.microfalx.lang.StringUtils.NA_STRING;
import static net.microfalx.lang.StringUtils.isNotEmpty;

/**
 * An aggregation of an application alert/problem.
 * <p>
 * Alerts are indirectly created from events happening in the system, like logger events, exceptions, etc.
 * <p>
 * Each alert represents an aggregation of a problem type and the problem type is identified by its identifier.
 * Each problem type should create a unique identifier.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Alert implements Identifiable<String>, Nameable, Timestampable<LocalDateTime> {

    private String id;
    private String message;
    private LoggerEvent.Level level;
    private volatile long createdAt;
    private volatile long modifiedAt;

    private volatile int totalEventCount;
    private volatile int pendingEventCount;
    private volatile boolean acknowledged;

    private volatile String failureClass;
    private volatile String failureType;
    private volatile String failureStackTrace;

    public String getName() {
        return message;
    }

    public String getMessage() {
        return message;
    }

    public LoggerEvent.Level getLevel() {
        return level;
    }

    public Severity getSeverity() {
        return switch (getLevel()) {
            case WARN -> Severity.WARN;
            case ERROR -> Severity.ERROR;
            default -> Severity.INFO;
        };
    }

    @Override
    public LocalDateTime getCreatedAt() {
        return TimeUtils.toLocalDateTime(createdAt);
    }

    @Override
    public LocalDateTime getModifiedAt() {
        return TimeUtils.toLocalDateTime(modifiedAt);
    }

    public enum Severity {
        INFO,
        WARN,
        ERROR
    }

    /**
     * Updates the alert with new information from a logger event.
     *
     * @param event the new event
     */
    public synchronized void update(LoggerEvent event) {
        requireNonNull(event);
        long timestamp = event.getTimestamp();
        if (createdAt == 0) {
            createdAt = timestamp;
            modifiedAt = createdAt;
        } else {
            if (timestamp > modifiedAt) modifiedAt = timestamp;
        }
        this.message = event.getMessage();
        this.level = event.getLevel();
        failureClass = event.getExceptionClassName();
        if (event.getException() != null) {
            failureType = EnumUtils.toLabel(Failure.of(event.getException()).getType());
        } else {
            failureType = isNotEmpty(failureClass) ? getRootCauseName(failureClass) : NA_STRING;
        }
        failureStackTrace = event.getExceptionStackTrace();
        acknowledged = false;
        totalEventCount++;
        pendingEventCount++;
    }
}
