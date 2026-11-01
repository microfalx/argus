package net.microfalx.argus.api;

import net.microfalx.lang.FormatterUtils;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void parsePercent() {
        assertThat(Unit.PERCENT.parse("85")).isEqualTo(85f);
        assertThat(Unit.PERCENT.parse("85%")).isEqualTo(85f);
        assertThat(Unit.PERCENT.parse(" 12.5 % ")).isEqualTo(12.5f);
        assertThat(Unit.PERCENT.parse("~0%")).isEqualTo(0f);
    }

    @Test
    void parseMilliseconds() {
        assertThat(Unit.MILLISECOND.parse("50")).isEqualTo(50f);
        assertThat(Unit.MILLISECOND.parse("50ms")).isEqualTo(50f);
        assertThat(Unit.MILLISECOND.parse("2s")).isEqualTo(2_000f);
        assertThat(Unit.MILLISECOND.parse("1s 500ms")).isEqualTo(1_500f);
        assertThat(Unit.MILLISECOND.parse("5m")).isEqualTo(300_000f);
        assertThat(Unit.MILLISECOND.parse("1h 30m")).isEqualTo(5_400_000f);
        assertThat(Unit.MILLISECOND.parse("1d")).isEqualTo(86_400_000f);
        assertThat(Unit.MILLISECOND.parse("500us")).isEqualTo(0.5f);
        assertThat(Unit.MILLISECOND.parse(Unit.MILLISECOND.format(2345f))).isEqualTo(2345f);
    }

    @Test
    void parseNanoseconds() {
        assertThat(Unit.NANOSECOND.parse("100")).isEqualTo(100f);
        assertThat(Unit.NANOSECOND.parse("100ns")).isEqualTo(100f);
        assertThat(Unit.NANOSECOND.parse("2us")).isEqualTo(2_000f);
        assertThat(Unit.NANOSECOND.parse("2\u03bcs")).isEqualTo(2_000f);
        assertThat(Unit.NANOSECOND.parse("1ms")).isEqualTo(1_000_000f);
    }

    @Test
    void parseCounter() {
        assertThat(Unit.COUNTER.parse("100000")).isEqualTo(100_000f);
        assertThat(Unit.COUNTER.parse("10k")).isEqualTo(10_000f);
        assertThat(Unit.COUNTER.parse("2M")).isEqualTo(2_000_000f);
    }

    @Test
    void parseBytes() {
        assertThat(Unit.BYTE.parse("100")).isEqualTo(100f);
        assertThat(Unit.BYTE.parse("100B")).isEqualTo(100f);
        assertThat(Unit.BYTE.parse("10KB")).isEqualTo(10_000f);
        assertThat(Unit.BYTE.parse("5 MB")).isEqualTo(5_000_000f);
        assertThat(Unit.BYTE.parse("1GB")).isEqualTo(1_000_000_000f);
    }

    @Test
    void parseThroughput() {
        assertThat(Unit.THROUGHPUT.parse("10")).isEqualTo(10f);
        assertThat(Unit.THROUGHPUT.parse("10r/s")).isEqualTo(10f);
        assertThat(Unit.THROUGHPUT.parse("2.5kr/s")).isEqualTo(2_500f);
    }

    @Test
    void parseInvalid() {
        assertThatThrownBy(() -> Unit.PERCENT.parse("abc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Unit.MILLISECOND.parse("5x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Unit.COUNTER.parse("")).isInstanceOf(IllegalArgumentException.class);
    }
}

