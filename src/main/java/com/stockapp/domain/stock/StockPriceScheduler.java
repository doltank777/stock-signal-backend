package com.stockapp.domain.stock;

import com.stockapp.domain.screening.realtime.KrxRegularMarketSessionPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Component
@Profile("!daily-price-load & !daily-price-update & !screening-run & !schema-validate")
@RequiredArgsConstructor
public class StockPriceScheduler {

    private final StockRepository stockRepository;
    private final StockPriceService stockPriceService;
    private final KrxTradingCalendar tradingCalendar;
    private final KrxRegularMarketSessionPolicy marketSessionPolicy;

    // 1시간마다 실행
    @Scheduled(cron = "0 0 9-16 * * MON-FRI", zone = "Asia/Seoul")
    public void collectStockPrices() {
        ZonedDateTime now = marketSessionPolicy.now();
        if (!marketSessionPolicy.isRegularMonitoringWindow(now)) {
            log.info("주식시장 운영 시간이 아니므로 현재가 수집을 건너뜁니다.");
            return;
        }

        try {
            if (!tradingCalendar.isTradingDay(now.toLocalDate())) {
                log.info("Stock price collection skipped - date: {}, reason: not a KRX trading day",
                        now.toLocalDate());
                return;
            }
        } catch (TradingCalendarUnavailableException e) {
            log.error("Stock price collection failed closed because KRX trading calendar "
                    + "is unavailable - date: {}", now.toLocalDate(), e);
            return;
        }

        List<Stock> stocks = stockRepository.findAll();

        log.info("주식 현재가 보조 수집 시작 - 대상 종목 수: {}", stocks.size());

        for (Stock stock : stocks) {
            try {
                stockPriceService.saveCurrentPriceFromKis(stock.getStockCode());
                log.info("현재가 저장 성공 - {}", stock.getStockCode());
                Thread.sleep(1200);
            } catch (Exception e) {
                log.error("현재가 저장 실패 - {}", stock.getStockCode(), e);
            }
        }

        log.info("주식 현재가 보조 수집 완료");
    }

}
