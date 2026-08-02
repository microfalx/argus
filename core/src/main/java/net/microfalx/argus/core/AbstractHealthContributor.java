package net.microfalx.argus.core;

import net.microfalx.argus.api.*;

import java.io.File;
import java.time.Duration;

/**
 * Base class for all health contributors.
 */
public abstract class AbstractHealthContributor implements HealthContributor, HealthProvider {

    protected final Object lock = new Object();

    @Override
    public final Health getHealth(Resource.Type type) {
        return HealthService.getInstance().getHealth(type);
    }

    protected final Health.Item asPercentageItem(Thresholds thresholds, float value) {
        return Health.Item.create(thresholds, value, Unit.PERCENT);
    }

    protected final Health.Item asPercentageItem(Thresholds thresholds, File file) {
        long total = file.getTotalSpace();
        long used = file.getTotalSpace() - file.getUsableSpace();
        return Health.Item.create(thresholds, used, total, Unit.BYTE);
    }

    protected final Health.Item asCounterItem(Thresholds thresholds, int value) {
        return Health.Item.create(thresholds, value, Unit.COUNTER);
    }

    protected final Health.Item asDurationItem(Thresholds thresholds, Duration value) {
        return Health.Item.create(thresholds, value.toMillis(), thresholds.getUnit());
    }


}
