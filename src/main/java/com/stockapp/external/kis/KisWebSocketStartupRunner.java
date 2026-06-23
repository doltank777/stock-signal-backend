package com.stockapp.external.kis;

import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class KisWebSocketStartupRunner implements ApplicationRunner {

    private final StockRepository stockRepository;
    private final KisWebSocketClient kisWebSocketClient;

    @Override
    public void run(ApplicationArguments args) {
        List<String> stockCodes = stockRepository.findAll()
                .stream()
                .limit(5)
                .map(Stock::getStockCode)
                .toList();

        log.info("KIS WebSocket 자동 구독 시작 - 대상 종목 수: {}", stockCodes.size());

        kisWebSocketClient.connectAndSubscribe(stockCodes);

        log.info("KIS WebSocket 자동 구독 요청 완료");
    }
}