package net.microfalx.argus.spring.actuator;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.ToString;
import net.microfalx.argus.api.Health;
import net.microfalx.lang.IdentityAware;

import java.time.Duration;
import java.time.ZonedDateTime;

import static net.microfalx.lang.FormatterUtils.formatElapsed;

@Getter
@ToString
public class HealthMetadata extends IdentityAware<String> {

    private Health.Type type;
    private Health.Severity severity;
    private float score;
    private ZonedDateTime createdAt;
    private ZonedDateTime modifiedAt;
    private String age;
    private int groups;
    private int items;
    private String report;
    String reportPath;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String secretKey;

    public static HealthMetadata of(Health health) {
        HealthMetadata metadata = new HealthMetadata();
        metadata.setId(health.getId());
        metadata.type = health.getType();
        metadata.severity = health.getSeverity();
        metadata.score = health.getScore();
        metadata.createdAt = health.getCreatedAt();
        metadata.modifiedAt = health.getModifiedAt();
        metadata.groups = health.getGroups().size();
        metadata.items = health.getScored().size();
        metadata.report = health.getReport();
        metadata.age = formatElapsed(Duration.between(health.getModifiedAt(), ZonedDateTime.now()));
        return metadata;
    }
}
