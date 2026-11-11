package net.microfalx.argus.api;

/**
 * A listener which is notified when the health of a {@link Resource} changes zones.
 * <p>
 * A resource enters the <i>red zone</i> when its score drops below {@link Health#ERROR} and it
 * goes back to normal when its score recovers to {@link Health#WARNING} or above. The gap between the
 * two thresholds avoids repeated notifications when the score fluctuates around a single threshold.
 * <p>
 * Each transition is reported once: a resource in the red zone is not reported again until it
 * recovered first.
 * <p>
 * Listeners are discovered from the class path (annotated with {@link net.microfalx.lang.annotation.Provider})
 * or registered with {@link HealthService#register(HealthListener)}.
 */
public interface HealthListener {

    /**
     * Invoked when the score of a resource enters the red zone.
     *
     * @param resource the resource, with the health which triggered the notification
     */
    default void onUnhealthy(Resource resource) {
        // empty by default
    }

    /**
     * Invoked when the score of a resource goes back to normal, after it was in the red zone.
     *
     * @param resource the resource, with the health which triggered the notification
     */
    default void onHealthy(Resource resource) {
        // empty by default
    }
}
