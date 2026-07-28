package net.microfalx.argus.api;

import net.microfalx.lang.FormatterUtils;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class UnitTest {

    @Test
    void formatPercentUsesPercentFormatter() {
        float value = 12.34f;
        assertThat(Unit.PERCENT.format(value)).isEqualTo(FormatterUtils.formatPercent(value));
    }

    @Test
    void formatCountUsesNumberFormatterWithoutDecimals() {
        float value = 1234f;
        assertThat(Unit.COUNTER.format(value)).isEqualTo(FormatterUtils.formatNumber(value, 0));
    }

    @Test
    void formatByteUsesByteFormatter() {
        float value = 12_345f;
        assertThat(Unit.BYTE.format(value)).isEqualTo(FormatterUtils.formatBytes(value));
    }

    @Test
    void formatThroughputUsesRequestsPerSecondFormatter() {
        float value = 2345f;
        assertThat(Unit.THROUGHPUT.format(value)).isEqualTo(FormatterUtils.formatThroughput(value, "r/s"));
    }

    @Test
    void formatThroughputUsesMillisFormatter() {
        float value = 2345f;
        assertThat(Unit.MILLISECOND.format(value)).isEqualTo(FormatterUtils.formatDuration(Duration.ofMillis((long) value)));
    }

    @Test
    void formatThroughputUsesNanosecondsFormatter() {
        float value = 2345f;
        assertThat(Unit.NANOSECOND.format(value)).isEqualTo(FormatterUtils.formatDuration(Duration.ofNanos((long) value)));
    }
}

