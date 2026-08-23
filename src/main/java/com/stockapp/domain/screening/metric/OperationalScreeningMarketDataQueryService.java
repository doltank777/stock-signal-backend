package com.stockapp.domain.screening.metric;

import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockDailyPrice;
import com.stockapp.domain.stock.StockDailyPriceRepository;
import com.stockapp.domain.stock.dto.DailyPriceData;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OperationalScreeningMarketDataQueryService {

    private static final MathContext CALCULATION_CONTEXT =
            MathContext.DECIMAL128;

    private final StockDailyPriceRepository repository;

    public OperationalScreeningMarketData load(
            Stock stock,
            LocalDate evaluationDate,
            OperationalScreeningDataRequirements requirements
    ) {
        validateInputs(stock, evaluationDate, requirements);
        int previousRowCount = requirements.requiredPreviousRowCount();
        List<StockDailyPrice> rows = repository
                .findByStockAndTradeDateLessThanEqualOrderByTradeDateDesc(
                        stock,
                        evaluationDate,
                        PageRequest.of(0, Math.addExact(previousRowCount, 1)));
        if (rows.isEmpty()
                || !evaluationDate.equals(rows.getFirst().getTradeDate())) {
            throw new OperationalScreeningDataMissingException(
                    "finalized daily price is missing for operational screening: "
                            + stock.getStockCode() + " on " + evaluationDate);
        }

        StockDailyPrice current = rows.getFirst();
        List<StockDailyPrice> previousRows = rows.subList(1, rows.size());
        Optional<BigDecimal> changeRate = requirements.changeRateRequired()
                ? calculateChangeRate(current, previousRows)
                : Optional.empty();
        List<DailyPriceData> history = toHistory(
                previousRows, requirements.maxHistoryPeriod());
        OperationalCurrentMetrics currentMetrics =
                new OperationalCurrentMetrics(
                        BigDecimal.valueOf(current.getClosePrice()),
                        changeRate,
                        BigDecimal.valueOf(current.getVolume()));
        return new OperationalScreeningMarketData(currentMetrics, history);
    }

    private Optional<BigDecimal> calculateChangeRate(
            StockDailyPrice current,
            List<StockDailyPrice> previousRows
    ) {
        if (previousRows.isEmpty()) {
            return Optional.empty();
        }
        long previousClose = previousRows.getFirst().getClosePrice();
        if (previousClose == 0L) {
            return Optional.empty();
        }
        return Optional.of(BigDecimal.valueOf(current.getClosePrice())
                .subtract(BigDecimal.valueOf(previousClose))
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(previousClose),
                        CALCULATION_CONTEXT));
    }

    private List<DailyPriceData> toHistory(
            List<StockDailyPrice> previousRows,
            int maxHistoryPeriod
    ) {
        List<StockDailyPrice> selected = new ArrayList<>(
                previousRows.subList(
                        0, Math.min(previousRows.size(), maxHistoryPeriod)));
        Collections.reverse(selected);
        return selected.stream()
                .map(row -> new DailyPriceData(
                        row.getTradeDate(), row.getClosePrice(), row.getVolume()))
                .toList();
    }

    private void validateInputs(
            Stock stock,
            LocalDate evaluationDate,
            OperationalScreeningDataRequirements requirements
    ) {
        if (stock == null) {
            throw new IllegalArgumentException("stock is required");
        }
        if (evaluationDate == null) {
            throw new IllegalArgumentException("evaluationDate is required");
        }
        if (requirements == null) {
            throw new IllegalArgumentException("requirements are required");
        }
    }
}
