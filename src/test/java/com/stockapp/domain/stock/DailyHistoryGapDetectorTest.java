package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.DailyHistoryGap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyHistoryGapDetectorTest {

    @Mock
    private StockDailyPriceRepository repository;

    private DailyHistoryGapDetector detector;
    private Stock stock;

    @BeforeEach
    void setUp() {
        detector = new DailyHistoryGapDetector(repository);
        stock = Stock.builder().id(1L).stockCode("005930")
                .stockName("Samsung Electronics")
                .marketType(MarketType.KOSPI).build();
    }

    @Test
    void returnsCompleteWithoutQueryForEmptyRequiredDates() {
        DailyHistoryGap result = detector.detect(stock, List.of());

        assertThat(result.complete()).isTrue();
        assertThat(result.missingTradingDates()).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void returnsCompleteWhenEveryRequiredDateExists() {
        List<LocalDate> required = dates(10, 11, 12);
        when(repository.findTradeDates(stock, date(10), date(12)))
                .thenReturn(required);

        DailyHistoryGap result = detector.detect(stock, required);

        assertThat(result.complete()).isTrue();
        assertThat(result.missingTradingDates()).isEmpty();
    }

    @Test
    void returnsEveryRequiredDateWhenNoHistoryExists() {
        List<LocalDate> required = dates(10, 11, 12);
        when(repository.findTradeDates(stock, date(10), date(12)))
                .thenReturn(List.of());

        assertThat(detector.detect(stock, required).missingTradingDates())
                .containsExactlyElementsOf(required);
    }

    @Test
    void detectsIntermediateGapsByDateSet() {
        List<LocalDate> required = dates(10, 11, 12, 13, 14);
        when(repository.findTradeDates(stock, date(10), date(14)))
                .thenReturn(dates(10, 12, 14));

        assertThat(detector.detect(stock, required).missingTradingDates())
                .containsExactly(date(11), date(13));
    }

    @Test
    void detectsIntermediateGapsEvenWhenLatestDateExists() {
        List<LocalDate> required = dates(10, 11, 12, 13, 14);
        when(repository.findTradeDates(stock, date(10), date(14)))
                .thenReturn(dates(10, 11, 14));

        assertThat(detector.detect(stock, required).missingTradingDates())
                .containsExactly(date(12), date(13));
    }

    @Test
    void detectsGapDespiteSameOverallHistoryCount() {
        List<LocalDate> required = dates(10, 11, 12, 13);
        when(repository.findTradeDates(stock, date(10), date(13)))
                .thenReturn(dates(10, 11, 13));

        assertThat(detector.detect(stock, required).missingTradingDates())
                .containsExactly(date(12));
        verify(repository, never()).countByStockAndTradeDateLessThanEqual(
                stock, date(13));
    }

    @Test
    void normalizesUnsortedDuplicateRequiredDates() {
        List<LocalDate> required = List.of(
                date(21), date(19), date(20), date(19));
        when(repository.findTradeDates(stock, date(19), date(21)))
                .thenReturn(List.of(date(20)));

        assertThat(detector.detect(stock, required).missingTradingDates())
                .containsExactly(date(19), date(21));
    }

    @Test
    void ignoresExistingDatesOutsideRequiredSetWithinQueryRange() {
        List<LocalDate> required = dates(10, 12, 14);
        when(repository.findTradeDates(stock, date(10), date(14)))
                .thenReturn(dates(10, 11, 12, 13, 14));

        assertThat(detector.detect(stock, required).complete()).isTrue();
    }

    @Test
    void rejectsNullInputsBeforeQuery() {
        assertThatThrownBy(() -> detector.detect(null, List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("stock is required");
        assertThatThrownBy(() -> detector.detect(stock, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("requiredTradingDates is required");
        assertThatThrownBy(() -> detector.detect(stock,
                java.util.Arrays.asList(date(10), null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("requiredTradingDates must not contain null");
        verifyNoInteractions(repository);
    }

    @Test
    void returnsImmutableMissingDates() {
        when(repository.findTradeDates(stock, date(10), date(10)))
                .thenReturn(List.of());
        DailyHistoryGap result = detector.detect(stock, List.of(date(10)));

        assertThatThrownBy(() -> result.missingTradingDates().add(date(11)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private List<LocalDate> dates(int... days) {
        return java.util.Arrays.stream(days).mapToObj(this::date).toList();
    }

    private LocalDate date(int day) {
        return LocalDate.of(2026, 8, day);
    }
}
