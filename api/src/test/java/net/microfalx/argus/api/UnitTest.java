package net.microfalx.argus.api;

import net.microfalx.lang.FormatterUtils;
import org.junit.jupiter.api.Test;

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
}

