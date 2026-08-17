package com.stockapp.domain.screening.metric;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ScreeningDataRequirementsTest {

    @Test
    void preservesSnapshotAndZeroDailyRequirements() {
        ScreeningDataRequirements requirements =
                new ScreeningDataRequirements(true, 0);

        assertThat(requirements.snapshotRequired()).isTrue();
        assertThat(requirements.maxDailyPeriod()).isZero();
    }

    @Test
    void allowsPositiveDailyPeriodWithoutSnapshot() {
        ScreeningDataRequirements requirements =
                new ScreeningDataRequirements(false, 20);

        assertThat(requirements.snapshotRequired()).isFalse();
        assertThat(requirements.maxDailyPeriod()).isEqualTo(20);
    }

    @Test
    void allowsNoMarketDataRequirements() {
        assertThat(new ScreeningDataRequirements(false, 0))
                .isEqualTo(new ScreeningDataRequirements(false, 0));
    }

    @Test
    void rejectsNegativeDailyPeriod() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ScreeningDataRequirements(false, -1));
    }
}
