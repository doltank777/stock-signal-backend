package com.stockapp.domain.stock;

import com.stockapp.domain.signal.SignalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockPriceScheduler {

    private final StockRepository stockRepository;
    private final StockPriceService stockPriceService;
    private final SignalService signalService;

    // 1분마다 실행
    @Scheduled(fixedDelay = 60000)
    public void collectStockPrices() {
        // 전체 종목
        // List<Stock> stocks = stockRepository.findAll();
        // 테스트용 종목 제한 5종목
        List<Stock> stocks = stockRepository.findAll()
                .stream()
                .limit(5)
                .toList();

        log.info("주식 현재가 자동 수집 시작 - 대상 종목 수: {}", stocks.size());

        for (Stock stock : stocks) {
            try {
                stockPriceService.saveCurrentPriceFromKis(stock.getStockCode());

                signalService.analyzeVolumeSpike(stock);

                // 이동평균 돌파 Signal 분석
                signalService.analyzeMovingAverageBreakout(stock);

                log.info("현재가 저장 성공 - {}", stock.getStockCode());

                Thread.sleep(1200);

            } catch (Exception e) {
                log.error("현재가 저장 실패 - {}", stock.getStockCode(), e);
            }
        }

        log.info("주식 현재가 자동 수집 완료");
    }
}