package net.microfalx.argus.api;

import net.microfalx.lang.Identifiable;
import net.microfalx.lang.Nameable;
import net.microfalx.lang.annotation.Tag;
import net.microfalx.metrics.Batch;

import java.util.Collection;

import static net.microfalx.lang.AnnotationUtils.getAnnotation;
import static net.microfalx.lang.StringUtils.isNotEmpty;
import static net.microfalx.lang.StringUtils.toIdentifier;

/**
 * A listener used to collect diagnostics information from services.
 */
public interface HealthContributor extends Identifiable<String>, Nameable {

    /**
     * Returns the identifier of the contributor, extracted from the first {@link Tag} value.
     * <p>
     * If the contributor is not tagged, the identifier is derived from the class name, without
     * the {@code HealthContributor} (or {@code Contributor}) suffix.
     * <p>
     * Unlike the name (which is meant for display and can change), the identifier is stable and it is used
     * to build keys, like the configuration keys of the thresholds.
     *
     * @return a non-null instance
     */
    @Override
    default String getId() {
        Tag tag = getAnnotation(this, Tag.class);
        if (tag != null && tag.value().length > 0 && isNotEmpty(tag.value()[0])) return tag.value()[0];
        String name = getClass().getSimpleName();
        if (name.endsWith("HealthContributor")) {
            name = name.substring(0, name.length() - "HealthContributor".length());
        } else if (name.endsWith("Contributor")) {
            name = name.substring(0, name.length() - "Contributor".length());
        }
        return toIdentifier(name);
    }

    /**
     * Returns which resource type is contributor supporting.
     *
     * @param type the resource type
     * @return {@code true} if the contributor supports the given resource type, {@code false} otherwise
     */
    boolean supports(Resource.Type type);

    /**
     * Invoked when the process (service replica) needs to be assessed.
     *
     * @param health the assessment to update
     */
    void update(Health health);

    /**
     * Invoked when the process (service replica) needs to update its metrics.
     *
     * @param batch the batch where the metrics will be accumulated
     */
    default void update(Batch batch) {
        // default is empty, some contributors do not produce metrics
    }

    /**
     * Invoked periodically to recalculate threshold limits (for those thresholds which depend on historical values).
     */
    default void updateStats() {
        // empty by default
    }

    /**
     * Returns default thresholds used by this health contributor.
     *
     * @return a non-null instance
     */
    Collection<Thresholds> getThresholds();
}
