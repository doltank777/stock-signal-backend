package com.stockapp.domain.screening.realtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LatestOperationalRealtimeSelectionRegistryTest {

    @Test
    void startsEmptyAndAtomicallyReplacesLatestImmutableSelection() {
        LatestOperationalRealtimeSelectionRegistry registry =
                new LatestOperationalRealtimeSelectionRegistry();
        assertThat(registry.findLatest()).isEmpty();

        OperationalRealtimeTargetSelection selection =
                OperationalRealtimeTargetSelection.empty();
        registry.replace(selection);

        assertThat(registry.findLatest()).containsSame(selection);
    }
}
