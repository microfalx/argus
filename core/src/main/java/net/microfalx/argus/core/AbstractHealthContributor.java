package net.microfalx.argus.core;

import net.microfalx.argus.api.Health;
import net.microfalx.argus.api.HealthContributor;
import net.microfalx.argus.api.Thresholds;
import net.microfalx.argus.api.Unit;

import java.io.File;
import java.time.Duration;

/**
 * Base class for all health contributors.
 */
public abstract class AbstractHealthContributor implements HealthContributor {

    protected final Health.Item asPercentageItem(Thresholds thresholds, float value) {
        return Health.Item.create(thresholds, value, Unit.PERCENT);
    }

    protected final Health.Item asPercentageItem(Thresholds thresholds, File file) {
        long total = file.getTotalSpace();
        long used = file.getTotalSpace() - file.getUsableSpace();
        return Health.Item.create(thresholds, used, total, Unit.BYTE);
    }

    protected final Health.Item asCounterItem(Thresholds thresholds, int value) {
        return Health.Item.create(thresholds, value);
    }

    protected final Health.Item asDurationItem(Thresholds thresholds, Duration value) {
        return Health.Item.create(thresholds, value.toMillis());
    }


}
