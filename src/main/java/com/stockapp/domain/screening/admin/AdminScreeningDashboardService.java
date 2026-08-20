package com.stockapp.domain.screening.admin;

import com.stockapp.domain.screening.LatestScreeningSnapshot;
import com.stockapp.domain.screening.LatestScreeningSnapshotRegistry;
import com.stockapp.domain.screening.admin.dto.AdminDashboardStockResponse;
import com.stockapp.domain.screening.admin.dto.AdminRealtimeWatchStatusResponse;
import com.stockapp.domain.screening.admin.dto.AdminScreeningConditionResultResponse;
import com.stockapp.domain.screening.admin.dto.AdminScreeningResultsResponse;
import com.stockapp.domain.screening.realtime.RealtimeWatchTarget;
import com.stockapp.domain.screening.realtime.RealtimeWatchTargetRegistry;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminScreeningDashboardService {

    public static final int REALTIME_WATCH_CAPACITY = 40;

    private static final Comparator<ConditionGroup> CONDITION_ORDER =
            Comparator.comparingInt(ConditionGroup::priority).reversed()
                    .thenComparing(ConditionGroup::searchConditionId);
    private static final Comparator<AdminDashboardStockResponse> STOCK_ORDER =
            Comparator.comparing(AdminDashboardStockResponse::stockCode);

    private final LatestScreeningSnapshotRegistry screeningSnapshotRegistry;
    private final RealtimeWatchTargetRegistry targetRegistry;
    private final StockRepository stockRepository;

    public AdminScreeningResultsResponse getScreeningResults() {
        return screeningSnapshotRegistry.findLatest()
                .map(this::toScreeningResponse)
                .orElseGet(AdminScreeningResultsResponse::empty);
    }

    @Transactional(readOnly = true)
    public AdminRealtimeWatchStatusResponse getRealtimeWatchStatus() {
        List<RealtimeWatchTarget> targets = targetRegistry.findAll().values()
                .stream()
                .sorted(Comparator.comparing(RealtimeWatchTarget::stockCode))
                .toList();
        if (targets.isEmpty()) {
            return new AdminRealtimeWatchStatusResponse(
                    0, REALTIME_WATCH_CAPACITY, List.of());
        }

        List<Long> stockIds = targets.stream()
                .map(RealtimeWatchTarget::stockId)
                .toList();
        Map<Long, Stock> stocksById = new LinkedHashMap<>();
        for (Stock stock : stockRepository.findAllById(stockIds)) {
            stocksById.put(stock.getId(), stock);
        }

        List<AdminDashboardStockResponse> stocks = targets.stream()
                .map(target -> toStockResponse(requireStock(
                        stocksById, target)))
                .sorted(STOCK_ORDER)
                .toList();
        return new AdminRealtimeWatchStatusResponse(
                stocks.size(), REALTIME_WATCH_CAPACITY, stocks);
    }

    private AdminScreeningResultsResponse toScreeningResponse(
            LatestScreeningSnapshot snapshot) {
        Map<Long, ConditionGroup> groups = new LinkedHashMap<>();
        for (LatestScreeningSnapshot.Candidate candidate : snapshot.candidates()) {
            AdminDashboardStockResponse stock = new AdminDashboardStockResponse(
                    candidate.stockId(), candidate.stockCode(),
                    candidate.stockName(), candidate.market());
            for (LatestScreeningSnapshot.Match match : candidate.matches()) {
                groups.computeIfAbsent(match.searchConditionId(), ignored ->
                                new ConditionGroup(
                                        match.searchConditionId(),
                                        match.searchConditionName(),
                                        match.priority(),
                                        match.realtimeEnabled(),
                                        new ArrayList<>()))
                        .stocks().add(stock);
            }
        }

        List<AdminScreeningConditionResultResponse> conditions = groups.values()
                .stream()
                .sorted(CONDITION_ORDER)
                .map(group -> {
                    List<AdminDashboardStockResponse> stocks = group.stocks()
                            .stream().sorted(STOCK_ORDER).toList();
                    return new AdminScreeningConditionResultResponse(
                            group.searchConditionId(), group.searchConditionName(),
                            group.priority(), group.realtimeEnabled(),
                            stocks.size(), stocks);
                })
                .toList();
        return new AdminScreeningResultsResponse(
                true, snapshot.baseDate(), conditions);
    }

    private Stock requireStock(
            Map<Long, Stock> stocksById,
            RealtimeWatchTarget target) {
        Stock stock = stocksById.get(target.stockId());
        if (stock == null || !stock.getStockCode().equals(target.stockCode())) {
            throw new IllegalStateException(
                    "realtime watch stock is missing or mismatched: "
                            + target.stockId());
        }
        return stock;
    }

    private AdminDashboardStockResponse toStockResponse(Stock stock) {
        return new AdminDashboardStockResponse(
                stock.getId(), stock.getStockCode(),
                stock.getStockName(), stock.getMarketType());
    }

    private record ConditionGroup(
            Long searchConditionId,
            String searchConditionName,
            int priority,
            boolean realtimeEnabled,
            List<AdminDashboardStockResponse> stocks
    ) {
    }
}
