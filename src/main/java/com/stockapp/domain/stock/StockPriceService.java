package com.stockapp.domain.stock;

import com.stockapp.external.kis.KisStockClient;
import com.stockapp.external.kis.dto.KisStockPriceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockPriceService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final StockRepository stockRepository;
    private final StockPriceRepository stockPriceRepository;
    private final KisStockClient kisStockClient;
    private final Clock clock;

    @Transactional
    public StockPriceResponse savePrice(StockPriceRequest request) {
        if (!stockRepository.existsByStockCode(request.getStockCode())) {
            throw new IllegalArgumentException("등록되지 않은 종목입니다.");
        }

        LocalDateTime collectedAt = currentKoreaDateTime();
        StockPrice stockPrice = StockPrice.builder()
                .stockCode(request.getStockCode())
                .currentPrice(request.getCurrentPrice())
                .changeRate(request.getChangeRate())
                .volume(request.getVolume())
                .tradeDate(request.getTradeDate())
                .collectedAt(collectedAt)
                .build();

        StockPrice savedPrice = stockPriceRepository.save(stockPrice);

        return StockPriceResponse.from(savedPrice);
    }

    @Transactional
    public StockPriceResponse saveCurrentPriceFromKis(String stockCode) {
        if (!stockRepository.existsByStockCode(stockCode)) {
            throw new IllegalArgumentException("등록되지 않은 종목입니다.");
        }

        KisStockPriceResponse response = kisStockClient.getCurrentPrice(stockCode);

        if (!"0".equals(response.getRtCd())) {
            throw new IllegalArgumentException("KIS 현재가 조회 실패: " + response.getMsg());
        }

        KisStockPriceResponse.Output output = response.getOutput();

        LocalDateTime collectedAt = currentKoreaDateTime();
        StockPrice stockPrice = StockPrice.builder()
                .stockCode(stockCode)
                .currentPrice(Long.parseLong(output.getCurrentPrice()))
                .changeRate(Double.parseDouble(output.getChangeRate()))
                .volume(Long.parseLong(output.getVolume()))
                .tradeDate(collectedAt.toLocalDate())
                .collectedAt(collectedAt)
                .build();

        StockPrice savedPrice = stockPriceRepository.save(stockPrice);

        return StockPriceResponse.from(savedPrice);
    }

    public StockPriceResponse getLatestPrice(String stockCode) {
        StockPrice stockPrice = stockPriceRepository
                .findTopByStockCodeOrderByCollectedAtDesc(stockCode)
                .orElseThrow(() -> new IllegalArgumentException("가격 정보를 찾을 수 없습니다."));

        return StockPriceResponse.from(stockPrice);
    }

    private LocalDateTime currentKoreaDateTime() {
        return LocalDateTime.now(clock.withZone(KOREA_ZONE));
    }
}
