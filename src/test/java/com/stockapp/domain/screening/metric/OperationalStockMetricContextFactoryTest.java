package com.stockapp.domain.screening.metric;

import com.stockapp.domain.screening.ScreeningLogicalOperator;
import com.stockapp.domain.screening.ScreeningMetric;
import com.stockapp.domain.screening.ScreeningOperator;
import com.stockapp.domain.screening.ScreeningStage;
import com.stockapp.domain.screening.SearchConditionRule;
import com.stockapp.domain.screening.rule.RuleEvaluationSupport;
import com.stockapp.domain.screening.rule.ScreeningRuleEvaluator;
import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.dto.DailyPriceData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationalStockMetricContextFactoryTest {

    private static final LocalDate D = LocalDate.of(2026, 8, 21);

    private OperationalScreeningMarketDataQueryService queryService;
    private OperationalStockMetricContextFactory factory;
    private ScreeningMetricCalculator calculator;
    private Stock stock;

    @BeforeEach
    void setUp() {
        queryService = mock(OperationalScreeningMarketDataQueryService.class);
        factory = new OperationalStockMetricContextFactory(
                queryService,
                new OperationalScreeningDataRequirementAnalyzer(
                        new ScreeningDataRequirementAnalyzer()));
        calculator = new ScreeningMetricCalculator(
                new StockMetricCalculationSupport());
        stock = Stock.builder()
                .id(1L)
                .stockCode("005930")
                .stockName("Samsung Electronics")
                .marketType(MarketType.KOSPI)
                .build();
    }

    @Test
    void reusesExistingMetricContractWithFinalizedCurrentAndPriorHistory() {
        OperationalScreeningDataRequirements requirements =
                new OperationalScreeningDataRequirements(3, true);
        when(queryService.load(stock, D, requirements)).thenReturn(
                new OperationalScreeningMarketData(
                        new OperationalCurrentMetrics(
                                BigDecimal.valueOf(250),
                                Optional.of(BigDecimal.valueOf(25)),
                                BigDecimal.valueOf(200)),
                        List.of(
                                daily(D.minusDays(3), 100, 100),
                                daily(D.minusDays(2), 150, 100),
                                daily(D.minusDays(1), 200, 100))));

        StockMetricContext context = factory.createWithRequirements(
                stock, requirements, D);

        assertThat(value(ScreeningMetric.CURRENT_PRICE, null, context))
                .isEqualByComparingTo("250");
        assertThat(value(ScreeningMetric.VOLUME, null, context))
                .isEqualByComparingTo("200");
        assertThat(value(ScreeningMetric.CHANGE_RATE, null, context))
                .isEqualByComparingTo("25.0");
        assertThat(value(ScreeningMetric.VOLUME_RATIO, 2, context))
                .isEqualByComparingTo("2.0");
        assertThat(value(ScreeningMetric.MOVING_AVERAGE, 3, context))
                .isEqualByComparingTo("150");

        ScreeningRuleEvaluator evaluator = new ScreeningRuleEvaluator(
                calculator, new RuleEvaluationSupport());
        SearchConditionRule priceAboveMa = SearchConditionRule.createMetricRule(
                ScreeningStage.SCREENING,
                ScreeningMetric.CURRENT_PRICE, null,
                ScreeningOperator.GREATER_THAN,
                ScreeningMetric.MOVING_AVERAGE, 3,
                ScreeningLogicalOperator.AND, 1);
        assertThat(evaluator.evaluate(priceAboveMa, context)).isTrue();
    }

    @Test
    void nullableDerivedChangeRateIsUnavailableWithoutAffectingOtherMetrics() {
        OperationalScreeningDataRequirements requirements =
                new OperationalScreeningDataRequirements(0, true);
        when(queryService.load(stock, D, requirements)).thenReturn(
                new OperationalScreeningMarketData(
                        new OperationalCurrentMetrics(
                                BigDecimal.valueOf(110),
                                Optional.empty(),
                                BigDecimal.valueOf(987654)),
                        List.of()));

        StockMetricContext context = factory.createWithRequirements(
                stock, requirements, D);

        assertThat(calculator.calculate(
                ScreeningMetric.CHANGE_RATE, null, context)).isEmpty();
        assertThat(value(ScreeningMetric.CURRENT_PRICE, null, context))
                .isEqualByComparingTo("110");
        assertThat(value(ScreeningMetric.VOLUME, null, context))
                .isEqualByComparingTo("987654");
    }

    private BigDecimal value(
            ScreeningMetric metric,
            Integer period,
            StockMetricContext context) {
        Optional<BigDecimal> result = calculator.calculate(
                metric, period, context);
        assertThat(result).isPresent();
        return result.orElseThrow();
    }

    private DailyPriceData daily(
            LocalDate tradeDate, long closePrice, long volume) {
        return new DailyPriceData(tradeDate, closePrice, volume);
    }
}
