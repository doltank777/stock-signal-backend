package com.stockapp.domain.screening.realtime;

import com.stockapp.domain.stock.MarketType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RealtimeTargetDiffPlannerTest {

    private final RealtimeTargetDiffPlanner planner =
            new RealtimeTargetDiffPlanner(new RealtimeWatchTargetMapper());

    @Test
    void noOpUsesDesiredSnapshotAndRequiresNoUpdates() {
        var selection = selection(desired(1L, "A", 3L, 1L));
        var diff = planner.plan(
                selection,
                List.of(current(1L, "A", 1L, 3L)),
                Set.of("A"));

        assertThat(diff.unchangedTargets()).containsExactly(
                current(1L, "A", 1L, 3L));
        assertThat(diff.metadataChangedTargets()).isEmpty();
        assertThat(diff.toUnsubscribeStockCodes()).isEmpty();
        assertThat(diff.toSubscribeTargets()).isEmpty();
        assertThat(diff.requiresPhysicalChanges()).isFalse();
        assertThat(diff.requiresRegistryUpdate()).isFalse();
    }

    @Test
    void conditionOnlyChangeRequiresRegistryUpdateWithoutPhysicalCommands() {
        var diff = planner.plan(
                selection(desired(1L, "A", 1L, 3L)),
                List.of(current(1L, "A", 1L)),
                Set.of("A"));

        assertThat(diff.metadataChangedTargets()).containsExactly(
                current(1L, "A", 1L, 3L));
        assertThat(diff.requiresPhysicalChanges()).isFalse();
        assertThat(diff.requiresRegistryUpdate()).isTrue();
    }

    @Test
    void conditionOrderUsesSetSemantics() {
        var diff = planner.plan(
                selection(desired(1L, "A", 3L, 1L)),
                List.of(current(1L, "A", 1L, 3L)),
                Set.of("A"));

        assertThat(diff.unchangedTargets()).hasSize(1);
        assertThat(diff.metadataChangedTargets()).isEmpty();
    }

    @Test
    void addRemoveAndReplaceUsePhysicalMembership() {
        assertThat(planner.plan(
                selection(desired(1L, "A", 1L),
                        desired(2L, "B", 2L)),
                List.of(current(1L, "A", 1L)), Set.of("A"))
                .toSubscribeTargets())
                .extracting(RealtimeWatchTarget::stockCode)
                .containsExactly("B");

        assertThat(planner.plan(
                selection(desired(1L, "A", 1L)),
                List.of(current(1L, "A", 1L),
                        current(2L, "B", 2L)), Set.of("A", "B"))
                .toUnsubscribeStockCodes()).containsExactly("B");

        var replacement = planner.plan(
                selection(desired(2L, "B", 2L),
                        desired(3L, "C", 3L),
                        desired(4L, "D", 4L)),
                List.of(current(1L, "A", 1L),
                        current(2L, "B", 2L),
                        current(3L, "C", 3L)),
                Set.of("A", "B", "C"));
        assertThat(replacement.toUnsubscribeStockCodes())
                .containsExactly("A");
        assertThat(replacement.toSubscribeTargets())
                .extracting(RealtimeWatchTarget::stockCode)
                .containsExactly("D");
        assertThat(replacement.expectedFinalPhysicalCount()).isEqualTo(3);
    }

    @Test
    void identifiesOrphanPhysicalAndStaleRegistryTargets() {
        var diff = planner.plan(
                selection(desired(1L, "A", 1L),
                        desired(2L, "B", 2L)),
                List.of(current(1L, "A", 1L),
                        current(2L, "B", 2L),
                        current(3L, "C", 3L)),
                Set.of("A", "B", "D"));

        assertThat(diff.orphanPhysicalStockCodes()).containsExactly("D");
        assertThat(diff.toUnsubscribeStockCodes()).containsExactly("D");
        assertThat(diff.staleRegistryTargets())
                .extracting(RealtimeWatchTarget::stockCode)
                .containsExactly("C");
    }

    @Test
    void missingRegistryMetadataIsChangedWithoutResubscribe() {
        var diff = planner.plan(
                selection(desired(1L, "A", 1L),
                        desired(2L, "B", 2L),
                        desired(3L, "C", 3L)),
                List.of(current(1L, "A", 1L),
                        current(2L, "B", 2L)),
                Set.of("A", "B", "C"));

        assertThat(diff.toSubscribeTargets()).isEmpty();
        assertThat(diff.metadataChangedTargets())
                .extracting(RealtimeWatchTarget::stockCode)
                .containsExactly("C");
        assertThat(diff.orphanPhysicalStockCodes()).containsExactly("C");
    }

    @Test
    void registryMembershipNeverHidesMissingPhysicalSubscription() {
        var diff = planner.plan(
                selection(desired(1L, "A", 1L),
                        desired(2L, "B", 2L),
                        desired(3L, "C", 3L)),
                List.of(current(1L, "A", 1L),
                        current(2L, "B", 2L),
                        current(3L, "C", 3L)),
                Set.of("A", "B"));

        assertThat(diff.toSubscribeTargets())
                .extracting(RealtimeWatchTarget::stockCode)
                .containsExactly("C");
        assertThat(diff.staleRegistryTargets())
                .extracting(RealtimeWatchTarget::stockCode)
                .containsExactly("C");
        assertThat(diff.requiresPhysicalChanges()).isTrue();
        assertThat(diff.requiresRegistryUpdate()).isFalse();
    }

    @Test
    void sameStockCodeWithDifferentStockIdFailsFast() {
        assertThatThrownBy(() -> planner.plan(
                selection(desired(99L, "A", 1L)),
                List.of(current(1L, "A", 1L)), Set.of("A")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same stockId");
    }

    @Test
    void emptyDesiredUnsubscribesEveryPhysicalCodeInSortedOrder() {
        var diff = planner.plan(selection(), List.of(
                        current(1L, "A", 1L),
                        current(2L, "B", 2L),
                        current(3L, "C", 3L)),
                new HashSet<>(List.of("C", "A", "B")));

        assertThat(diff.toUnsubscribeStockCodes())
                .containsExactly("A", "B", "C");
        assertThat(diff.expectedFinalPhysicalCount()).isZero();
    }

    @Test
    void legacyFortyFourPhysicalTargetsCanConvergeToForty() {
        List<DesiredRealtimeTarget> desired = new ArrayList<>();
        List<RealtimeWatchTarget> current = new ArrayList<>();
        Set<String> physical = new HashSet<>();
        for (int index = 0; index < 44; index++) {
            String code = code(index);
            current.add(current((long) index, code, (long) index));
            physical.add(code);
            if (index < 40) {
                desired.add(desired(
                        (long) index, code, (long) index));
            }
        }

        var diff = planner.plan(selection(desired), current, physical);

        assertThat(diff.currentPhysicalCount()).isEqualTo(44);
        assertThat(diff.toUnsubscribeStockCodes())
                .containsExactly(code(40), code(41), code(42), code(43));
        assertThat(diff.toSubscribeTargets()).isEmpty();
        assertThat(diff.expectedFinalPhysicalCount()).isEqualTo(40);
    }

    @Test
    void desiredOverCapacityFailsFast() {
        List<DesiredRealtimeTarget> desired = new ArrayList<>();
        for (int index = 0; index < 41; index++) {
            desired.add(desired((long) index, code(index), (long) index));
        }
        var invalidSelection = new OperationalRealtimeTargetSelection(
                41, 41, desired, List.of());

        assertThatThrownBy(() -> planner.plan(
                invalidSelection, List.of(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds realtime capacity");
    }

    @Test
    void subscribeOrderPreservesDesiredRankAndResultIsImmutable() {
        var diff = planner.plan(selection(
                        desired(3L, "C", 3L),
                        desired(1L, "A", 1L),
                        desired(2L, "B", 2L)),
                List.of(), Set.of());

        assertThat(diff.toSubscribeTargets())
                .extracting(RealtimeWatchTarget::stockCode)
                .containsExactly("C", "A", "B");
        assertThatThrownBy(() -> diff.toSubscribeTargets().add(
                current(4L, "D", 4L)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private OperationalRealtimeTargetSelection selection(
            DesiredRealtimeTarget... targets
    ) {
        return selection(List.of(targets));
    }

    private OperationalRealtimeTargetSelection selection(
            List<DesiredRealtimeTarget> targets
    ) {
        return new OperationalRealtimeTargetSelection(
                RealtimeWatchPolicy.CAPACITY, targets.size(),
                targets, List.of());
    }

    private DesiredRealtimeTarget desired(
            Long stockId,
            String stockCode,
            Long... conditionIds
    ) {
        List<DesiredRealtimeCondition> conditions =
                java.util.Arrays.stream(conditionIds)
                        .map(id -> new DesiredRealtimeCondition(
                                id, "condition-" + id, 100, 50))
                        .toList();
        DesiredRealtimeCondition best = conditions.getFirst();
        return new DesiredRealtimeTarget(
                stockId, stockCode, "stock-" + stockCode,
                MarketType.KOSPI, best.priority(), best.screeningScore(),
                conditions);
    }

    private RealtimeWatchTarget current(
            Long stockId,
            String stockCode,
            Long... conditionIds
    ) {
        return new RealtimeWatchTarget(
                stockId, stockCode, List.of(conditionIds));
    }

    private String code(int index) {
        return "%06d".formatted(index);
    }
}
