package com.stockapp.domain.screening.realtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RealtimeWatchTargetRegistryTest {

    private final RealtimeWatchTargetRegistry registry =
            new RealtimeWatchTargetRegistry();

    @Test
    void replacesWholeSnapshotAndSupportsLookup() {
        RealtimeWatchTarget first = target(1L, "A", 1L);
        RealtimeWatchTarget second = target(2L, "B", 2L);
        registry.replace(List.of(first, second));
        Map<String, RealtimeWatchTarget> oldSnapshot = registry.findAll();

        RealtimeWatchTarget third = target(3L, "C", 3L);
        RealtimeWatchTarget fourth = target(4L, "D", 4L);
        registry.replace(List.of(third, fourth));

        assertThat(registry.findAll()).containsOnlyKeys("C", "D");
        assertThat(registry.findByStockCode("A")).isEmpty();
        assertThat(registry.findByStockCode("C")).containsSame(third);
        assertThat(registry.size()).isEqualTo(2);
        assertThat(registry.isEmpty()).isFalse();
        assertThat(oldSnapshot).containsOnlyKeys("A", "B");
    }

    @Test
    void replacingWithEmptyListClearsRegistry() {
        registry.replace(List.of(target(1L, "A", 1L)));

        registry.replace(List.of());

        assertThat(registry.findAll()).isEmpty();
        assertThat(registry.size()).isZero();
        assertThat(registry.isEmpty()).isTrue();
    }

    @Test
    void copiesInputAndExposesUnmodifiableOrderedSnapshot() {
        RealtimeWatchTarget first = target(1L, "B", 1L);
        RealtimeWatchTarget second = target(2L, "A", 2L);
        List<RealtimeWatchTarget> source = new ArrayList<>(
                List.of(first, second));

        registry.replace(source);
        source.clear();

        assertThat(registry.findAll().keySet()).containsExactly("B", "A");
        assertThatThrownBy(() -> registry.findAll().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private RealtimeWatchTarget target(
            Long stockId,
            String stockCode,
            Long conditionId
    ) {
        return new RealtimeWatchTarget(
                stockId, stockCode, List.of(conditionId));
    }
}
