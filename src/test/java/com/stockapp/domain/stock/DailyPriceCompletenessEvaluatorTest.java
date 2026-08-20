package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.DailyPriceCompletenessResult;
import com.stockapp.domain.stock.dto.DailyPriceFinalizationBatchResult;
import com.stockapp.domain.stock.dto.DailyPriceFinalizationTarget;
import com.stockapp.domain.stock.dto.DailyPriceLoadFailure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyPriceCompletenessEvaluatorTest {

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 8, 20);
    private static final List<DailyPriceFinalizationTarget> TARGETS = List.of(
            target(1L, "000001"), target(2L, "000002"),
            target(3L, "000003"));

    @Mock StockDailyPriceRepository repository;

    @Test
    void readyWhenEveryTargetRowExistsAndThereAreNoFailures() {
        when(repository.findStockIdsWithPriceOnDate(
                TARGET_DATE, List.of(1L, 2L, 3L)))
                .thenReturn(List.of(1L, 2L, 3L));

        DailyPriceCompletenessResult result = evaluator().evaluate(
                batch(List.of(), List.of()));

        assertThat(result.ready()).isTrue();
        assertThat(result.presentRowCount()).isEqualTo(3);
        assertThat(result.missingStockCount()).isZero();
        verify(repository).findStockIdsWithPriceOnDate(
                TARGET_DATE, List.of(1L, 2L, 3L));
    }

    @Test
    void missingRowMakesNotReadyAndPreservesTargetOrder() {
        when(repository.findStockIdsWithPriceOnDate(
                TARGET_DATE, List.of(1L, 2L, 3L)))
                .thenReturn(List.of(1L, 3L));

        DailyPriceCompletenessResult result = evaluator().evaluate(
                batch(List.of(), List.of("000002")));

        assertThat(result.ready()).isFalse();
        assertThat(result.presentRowCount()).isEqualTo(2);
        assertThat(result.missingStockCodes()).containsExactly("000002");
        assertThat(result.noDataStockCodes()).containsExactly("000002");
    }

    @Test
    void failureMakesNotReadyEvenWhenAllRowsExist() {
        when(repository.findStockIdsWithPriceOnDate(
                TARGET_DATE, List.of(1L, 2L, 3L)))
                .thenReturn(List.of(1L, 2L, 3L));
        DailyPriceLoadFailure failure = new DailyPriceLoadFailure(
                "000002", "B", "FINALIZATION_FAILED", null);

        DailyPriceCompletenessResult result = evaluator().evaluate(
                batch(List.of(failure), List.of()));

        assertThat(result.ready()).isFalse();
        assertThat(result.missingStockCount()).isZero();
        assertThat(result.failedStockCodes()).containsExactly("000002");
    }

    @Test
    void noDataMakesNotReadyEvenWhenAnOldRowAlreadyExists() {
        when(repository.findStockIdsWithPriceOnDate(
                TARGET_DATE, List.of(1L, 2L, 3L)))
                .thenReturn(List.of(1L, 2L, 3L));

        DailyPriceCompletenessResult result = evaluator().evaluate(
                batch(List.of(), List.of("000002")));

        assertThat(result.ready()).isFalse();
        assertThat(result.missingStockCount()).isZero();
        assertThat(result.noDataStockCodes()).containsExactly("000002");
    }

    @Test
    void emptyUniverseAvoidsInvalidInQueryAndIsReady() {
        DailyPriceFinalizationBatchResult empty = new DailyPriceFinalizationBatchResult(
                TARGET_DATE, 0, 0, 0, 0, 0, 0, 0, true,
                Instant.EPOCH, Instant.EPOCH, List.of(), List.of(), List.of());

        DailyPriceCompletenessResult result = evaluator().evaluate(empty);

        assertThat(result.ready()).isTrue();
        verify(repository, never()).findStockIdsWithPriceOnDate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void resultCollectionsAreImmutableCopies() {
        List<String> noData = new ArrayList<>(List.of("000002"));
        DailyPriceFinalizationBatchResult batch = batch(List.of(), noData);
        noData.clear();
        when(repository.findStockIdsWithPriceOnDate(
                TARGET_DATE, List.of(1L, 2L, 3L)))
                .thenReturn(List.of(1L, 3L));

        DailyPriceCompletenessResult result = evaluator().evaluate(batch);

        assertThat(batch.noDataStockCodes()).containsExactly("000002");
        assertThatThrownBy(() -> result.missingStockCodes().add("999999"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private DailyPriceCompletenessEvaluator evaluator() {
        return new DailyPriceCompletenessEvaluator(repository);
    }

    private DailyPriceFinalizationBatchResult batch(
            List<DailyPriceLoadFailure> failures,
            List<String> noDataStockCodes
    ) {
        return new DailyPriceFinalizationBatchResult(
                TARGET_DATE, 3, 1, 1, 1, noDataStockCodes.size(),
                failures.size(), 3, true, Instant.EPOCH, Instant.EPOCH,
                failures, noDataStockCodes, TARGETS);
    }

    private static DailyPriceFinalizationTarget target(Long id, String code) {
        return new DailyPriceFinalizationTarget(id, code, code);
    }
}
