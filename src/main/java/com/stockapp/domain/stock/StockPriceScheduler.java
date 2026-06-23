package com.stockapp.domain.stock;

import com.stockapp.domain.signal.SignalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockPriceScheduler {

    private final StockRepository stockRepository;
    private final StockPriceService stockPriceService;
    private final SignalService signalService;

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalTime MARKET_OPEN_TIME = LocalTime.of(9, 0);
    private static final LocalTime MARKET_CLOSE_TIME = LocalTime.of(15, 30);

    // 1시간마다 실행
    @Scheduled(cron = "0 0 9-15 * * MON-FRI", zone = "Asia/Seoul")
    public void collectStockPrices() {
        if (!isMarketOpen()) {
            log.info("주식시장 운영 시간이 아니므로 현재가 수집을 건너뜁니다.");
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

    private boolean isMarketOpen() {
        ZonedDateTime now = ZonedDateTime.now(KOREA_ZONE);

        DayOfWeek dayOfWeek = now.getDayOfWeek();
        LocalTime currentTime = now.toLocalTime();

        boolean isWeekday = dayOfWeek != DayOfWeek.SATURDAY
                && dayOfWeek != DayOfWeek.SUNDAY;

        boolean isMarketTime = !currentTime.isBefore(MARKET_OPEN_TIME)
                && !currentTime.isAfter(MARKET_CLOSE_TIME);

        return isWeekday && isMarketTime;
    }
}