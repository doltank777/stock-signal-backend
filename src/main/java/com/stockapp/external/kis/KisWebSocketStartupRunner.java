package com.stockapp.external.kis;

import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Profile("local & !daily-price-load & !daily-price-update & !screening-run")
@RequiredArgsConstructor
public class KisWebSocketStartupRunner implements ApplicationRunner {

    private static final int SUBSCRIBE_CHUNK_SIZE = 40;
    private static final int TEST_STOCK_LIMIT = 40;
    private static final long SESSION_CONNECT_INTERVAL_MILLIS = 1_000L;

    private final StockRepository stockRepository;
    private final KisWebSocketClient kisWebSocketClient;

    @Override
    public void run(ApplicationArguments args) {
        List<String> stockCodes = stockRepository.findAll()
                .stream()
                .limit(TEST_STOCK_LIMIT)
                .map(Stock::getStockCode)
                .toList();

        List<List<String>> chunks = partition(stockCodes, SUBSCRIBE_CHUNK_SIZE);

        log.info(
                "KIS WebSocket 자동 구독 시작 - 전체 대상 종목 수: {}, 세션 수: {}, 세션당 최대 종목 수: {}",
                stockCodes.size(),
                chunks.size(),
                SUBSCRIBE_CHUNK_SIZE
        );

        for (int i = 0; i < chunks.size(); i++) {
            List<String> chunk = chunks.get(i);

            log.info(
                    "KIS WebSocket 세션 구독 시작 - sessionIndex: {}, targetCount: {}",
                    i + 1,
                    chunk.size()
            );

            kisWebSocketClient.connectAndSubscribe(chunk);

            sleep(SESSION_CONNECT_INTERVAL_MILLIS);
        }

        log.info("KIS WebSocket 자동 구독 요청 완료");
    }

    private List<List<String>> partition(List<String> stockCodes, int chunkSize) {
        List<List<String>> chunks = new ArrayList<>();

        for (int start = 0; start < stockCodes.size(); start += chunkSize) {
            int end = Math.min(start + chunkSize, stockCodes.size());
            chunks.add(stockCodes.subList(start, end));
        }

        return chunks;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("KIS WebSocket 세션 생성 대기 중 인터럽트 발생", e);
        }
    }
}
