package net.microfalx.argus.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThresholdTest {

    @Test
    void warnCreatesWarnThresholdWithCorrectValue() {
        Threshold threshold = Threshold.warn(42f);
        assertThat(threshold.getType()).isEqualTo(Threshold.Type.WARN);
        assertThat(threshold.getValue()).isEqualTo(42f);
    }

    @Test
    void errorCreatesErrorThresholdWithCorrectValue() {
        Threshold threshold = Threshold.error(75f);
        assertThat(threshold.getType()).isEqualTo(Threshold.Type.ERROR);
        assertThat(threshold.getValue()).isEqualTo(75f);
    }

    @Test
    void createWithTypeAndValueBuildsThreshold() {
        Threshold warn = Threshold.create(Threshold.Type.WARN, 10f);
        assertThat(warn.getType()).isEqualTo(Threshold.Type.WARN);
        assertThat(warn.getValue()).isEqualTo(10f);

        Threshold error = Threshold.create(Threshold.Type.ERROR, 20f);
        assertThat(error.getType()).isEqualTo(Threshold.Type.ERROR);
        assertThat(error.getValue()).isEqualTo(20f);
    }

    @Test
    void defaultUnitIsCounter() {
        Threshold threshold = Threshold.warn(50f);
        assertThat(threshold.getUnit()).isEqualTo(Unit.COUNTER);
    }

    @Test
    void withUnitReturnsCopyWithNewUnitAndKeepsOriginalUnchanged() {
        Threshold original = Threshold.warn(50f);
        assertThat(original.getUnit()).isEqualTo(Unit.COUNTER);

        Threshold withPercent = original.withUnit(Unit.PERCENT);
        assertThat(withPercent).isNotSameAs(original);
        assertThat(withPercent.getUnit()).isEqualTo(Unit.PERCENT);
        assertThat(withPercent.getType()).isEqualTo(original.getType());
        assertThat(withPercent.getValue()).isEqualTo(original.getValue());
    }

    @Test
    void withUnitNullThrowsException() {
        Threshold threshold = Threshold.warn(50f);
        assertThatThrownBy(() -> threshold.withUnit(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withUnitSupportsByte() {
        Threshold threshold = Threshold.error(1024f).withUnit(Unit.BYTE);
        assertThat(threshold.getUnit()).isEqualTo(Unit.BYTE);
        assertThat(threshold.getValue()).isEqualTo(1024f);
    }

    @Test
    void withUnitSupportsThroughput() {
        Threshold threshold = Threshold.warn(500f).withUnit(Unit.THROUGHPUT);
        assertThat(threshold.getUnit()).isEqualTo(Unit.THROUGHPUT);
    }

    @Test
    void equalsAndHashCodeBasedOnTypeValueAndUnit() {
        Threshold a = Threshold.warn(50f);
        Threshold b = Threshold.warn(50f);
        Threshold c = Threshold.warn(50f).withUnit(Unit.PERCENT);
        Threshold d = Threshold.error(50f);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a).isNotEqualTo(c);
        assertThat(a).isNotEqualTo(d);
    }

    @Test
    void toStringContainsTypeAndValue() {
        Threshold threshold = Threshold.warn(30f);
        assertThat(threshold.toString()).contains("WARN").contains("30");
    }
}

