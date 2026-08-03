package net.microfalx.argus.api;

import lombok.*;
import net.microfalx.lang.Identifiable;
import net.microfalx.lang.TimeUtils;
import net.microfalx.lang.Timestampable;

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
public class Alert implements Identifiable<String>, Timestampable<LocalDateTime> {

    private String id;
    private volatile LoggerEvent event;
    private volatile long createdAt;
    private volatile long modifiedAt;

    private volatile int totalEventCount;
    private volatile int pendingEventCount;
    private volatile boolean acknowledged;

    private volatile String failureClass;
    private volatile String failureType;

    public String getMessage() {
        return event.getMessage();
    }

    public LoggerEvent.Level getLevel() {
        return event.getLevel();
    }

    @Override
    public LocalDateTime getCreatedAt() {
        return TimeUtils.toLocalDateTime(createdAt);
    }

    @Override
    public LocalDateTime getModifiedAt() {
        return TimeUtils.toLocalDateTime(modifiedAt);
    }

    /**
     * Updates the alert with new information from a logger event.
     *
     * @param event the new event
     */
    public synchronized void update(LoggerEvent event) {
        requireNonNull(event);
        this.event = event;
        long timestamp = event.getTimestamp();
        if (createdAt == 0) {
            createdAt = timestamp;
            modifiedAt = createdAt;
        } else {
            if (timestamp > modifiedAt) modifiedAt = timestamp;
        }
        failureClass = event.getExceptionClassName();
        failureType = isNotEmpty(failureClass) ? getRootCauseName(failureClass) : NA_STRING;
        acknowledged = false;
        totalEventCount++;
        pendingEventCount++;
    }
}
