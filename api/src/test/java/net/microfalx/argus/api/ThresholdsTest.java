package net.microfalx.argus.api;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThresholdsTest {

    @Test
    void createFromFloatsBuildsThresholds() {
        Thresholds thresholds = Thresholds.create("Test", 10f, 20f);
        assertThat(thresholds.getWarn().getValue()).isEqualTo(10f);
        assertThat(thresholds.getError().getValue()).isEqualTo(20f);

        thresholds = Thresholds.create("Test", Threshold.warn(10f), Threshold.error(20f));
        assertThat(thresholds.getWarn().getValue()).isEqualTo(10f);
        assertThat(thresholds.getError().getValue()).isEqualTo(20f);
    }

    @Test
    void withReverseReturnsNewInstance() {
        Thresholds thresholds = Thresholds.create("Test", 10f, 20f);
        Thresholds reversed = thresholds.withReverse(true);

        assertThat(thresholds.isReverse()).isFalse();
        assertThat(reversed.isReverse()).isTrue();
        assertThat(reversed.getWarn()).isEqualTo(thresholds.getWarn());
        assertThat(reversed.getError()).isEqualTo(thresholds.getError());
    }

    @Test
    void getScoreWithoutReverseInterpolatesAndClamps() {
        Thresholds thresholds = Thresholds.create("Test", 50f, 100f);

        assertThat(thresholds.getScore(10f)).isEqualTo(Health.MAX);
        assertThat(thresholds.getScore(30f)).isEqualTo(4.8f);
        assertThat(thresholds.getScore(50f)).isEqualTo(Health.WARNING);
        assertThat(thresholds.getScore(75f)).isEqualTo(3f);
        assertThat(thresholds.getScore(100f)).isEqualTo(Health.ERROR);
        assertThat(thresholds.getScore(-100f)).isEqualTo(Health.MAX);
        assertThat(thresholds.getScore(500f)).isEqualTo(Health.MIN);
    }

    @Test
    void getScoreWithReverseInterpolatesAndClamps() {
        Thresholds thresholds = Thresholds.create("Test", 50f, 100f).withReverse(true);

        assertThat(thresholds.getScore(100f)).isEqualTo(Health.WARNING);
        assertThat(thresholds.getScore(75f)).isEqualTo(3f);
        assertThat(thresholds.getScore(50f)).isEqualTo(Health.ERROR);
        assertThat(thresholds.getScore(500f)).isEqualTo(Health.MAX);
        assertThat(thresholds.getScore(-100f)).isEqualTo(Health.MIN);
    }

    @Test
    void getScoreWithPercentage() {
        Thresholds thresholds = Thresholds.create("Test", 50f, 80f, Unit.PERCENT);

        assertThat(thresholds.getScore(100f)).isEqualTo(Health.MIN);
        assertThat(thresholds.getScore(80f)).isEqualTo(Health.ERROR);
        assertThat(thresholds.getScore(75f)).isEqualTo(2.33f, Offset.offset(0.1f));
        assertThat(thresholds.getScore(50f)).isEqualTo(Health.WARNING);
        assertThat(thresholds.getScore(500f)).isEqualTo(Health.MIN);
        assertThat(thresholds.getScore(-100f)).isEqualTo(Health.MAX);
    }

    @Test
    void getScoreWithPercentageReversed() {
        Thresholds thresholds = Thresholds.create("Test", 50f, 80f, Unit.PERCENT).withReverse(true);

        assertThat(thresholds.getScore(100f)).isEqualTo(Health.MAX);
        assertThat(thresholds.getScore(80f)).isEqualTo(Health.WARNING);
        assertThat(thresholds.getScore(75f)).isEqualTo(3.66f, Offset.offset(0.1f));
        assertThat(thresholds.getScore(50f)).isEqualTo(Health.ERROR);
        assertThat(thresholds.getScore(500f)).isEqualTo(Health.MAX);
        assertThat(thresholds.getScore(-100f)).isEqualTo(Health.MIN);
    }

    @Test
    void getScoreReturnsWithIssuesWhenThresholdsAreEqual() {
        Thresholds thresholds = Thresholds.create("Test", 20f, 20f);

        assertThat(thresholds.getScore(20f)).isEqualTo(Health.WITH_ISSUES);
        assertThat(thresholds.getScore(200f)).isEqualTo(Health.WITH_ISSUES);
    }

    @Test
    void createWithNullWarnThresholdThrowsException() {
        assertThatThrownBy(() -> Thresholds.create("Test", null, Threshold.error(20f)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Argument cannot be NULL");
    }

    @Test
    void createWithNullErrorThresholdThrowsException() {
        assertThatThrownBy(() -> Thresholds.create("Test", Threshold.warn(10f), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Argument cannot be NULL");
    }

    @Test
    void createWithErrorLowerThanWarnThrowsException() {
        assertThatThrownBy(() -> Thresholds.create("Test", 20f, 10f))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be less than warn threshold");

        assertThatThrownBy(() -> Thresholds.create("Test", Threshold.warn(20f), Threshold.error(10f)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be less than warn threshold");
    }

    @Test
    void createWithEmptyNameThrowsException() {
        assertThatThrownBy(() -> Thresholds.create("", 10f, 20f))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Thresholds.create(null, 10f, 20f))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createWithMismatchedUnitsThrowsException() {
        Threshold warn = Threshold.warn(10f).withUnit(Unit.PERCENT);
        Threshold error = Threshold.error(20f).withUnit(Unit.BYTE);

        assertThatThrownBy(() -> Thresholds.create("Test", warn, error))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be different than warn threshold");
    }

    @Test
    void createWithUnitFactorySetsUnitOnBothThresholds() {
        Thresholds thresholds = Thresholds.create("Test", 50f, 80f, Unit.PERCENT);
        assertThat(thresholds.getUnit()).isEqualTo(Unit.PERCENT);
        assertThat(thresholds.getWarn().getUnit()).isEqualTo(Unit.PERCENT);
        assertThat(thresholds.getError().getUnit()).isEqualTo(Unit.PERCENT);
    }

    @Test
    void getNameReturnsName() {
        Thresholds thresholds = Thresholds.create("Memory Usage", 50f, 80f);
        assertThat(thresholds.getName()).isEqualTo("Memory Usage");
    }

    @Test
    void getUnitReturnsUnitFromWarnThreshold() {
        Thresholds counter = Thresholds.create("CPU", 50f, 80f);
        assertThat(counter.getUnit()).isEqualTo(Unit.COUNTER);

        Thresholds percent = Thresholds.create("Memory", 50f, 80f, Unit.PERCENT);
        assertThat(percent.getUnit()).isEqualTo(Unit.PERCENT);
    }

    @Test
    void getMinimumForPercentUnitReturnsZero() {
        Thresholds thresholds = Thresholds.create("Test", 50f, 80f, Unit.PERCENT);
        assertThat(thresholds.getMinimum()).isEqualTo(0f);
    }

    @Test
    void getMaximumForPercentUnitReturnsHundred() {
        Thresholds thresholds = Thresholds.create("Test", 50f, 80f, Unit.PERCENT);
        assertThat(thresholds.getMaximum()).isEqualTo(100f);
    }

    @Test
    void getMinimumForCounterNonReverseReturnsHalfOfWarn() {
        Thresholds thresholds = Thresholds.create("Test", 50f, 100f);
        assertThat(thresholds.getMinimum()).isEqualTo(25f);
    }

    @Test
    void getMaximumForCounterNonReverseReturnsTwiceWarn() {
        Thresholds thresholds = Thresholds.create("Test", 50f, 100f);
        assertThat(thresholds.getMaximum()).isEqualTo(100f);
    }

    @Test
    void getMinimumForCounterReverseReturnsHalfOfError() {
        Thresholds thresholds = Thresholds.create("Test", 50f, 100f).withReverse(true);
        assertThat(thresholds.getMinimum()).isEqualTo(50f);
    }

    @Test
    void getMaximumForCounterReverseReturnsTwiceError() {
        Thresholds thresholds = Thresholds.create("Test", 50f, 100f).withReverse(true);
        assertThat(thresholds.getMaximum()).isEqualTo(200f);
    }
}
