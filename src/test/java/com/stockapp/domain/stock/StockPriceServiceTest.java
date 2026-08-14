package com.stockapp.domain.stock;

import com.stockapp.external.kis.KisStockClient;
import com.stockapp.external.kis.dto.KisStockPriceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockPriceServiceTest {

    @Mock
    private StockRepository stockRepository;

    @Mock
    private StockPriceRepository stockPriceRepository;

    @Mock
    private KisStockClient kisStockClient;

    private ArgumentCaptor<StockPrice> priceCaptor;

    @BeforeEach
    void setUp() {
        priceCaptor = ArgumentCaptor.forClass(StockPrice.class);
    }

    @Test
    void savesKisSnapshotUsingKoreaDateAtUtcDateBoundary() {
        StockPriceService service = serviceAt("2026-08-13T15:30:00Z");
        prepareKisPrice("005930");

        StockPriceResponse response = service.saveCurrentPriceFromKis("005930");

        verify(stockPriceRepository).save(priceCaptor.capture());
        StockPrice savedPrice = priceCaptor.getValue();
        assertThat(savedPrice.getTradeDate())
                .isEqualTo(LocalDate.of(2026, 8, 14));
        assertThat(savedPrice.getCollectedAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 14, 0, 30));
        assertThat(response.getTradeDate()).isEqualTo(savedPrice.getTradeDate());
        assertThat(response.getCollectedAt()).isEqualTo(savedPrice.getCollectedAt());
    }

    @Test
    void savesKisSnapshotUsingKoreaDateTimeDuringMarketHours() {
        StockPriceService service = serviceAt("2026-08-14T03:00:00Z");
        prepareKisPrice("005930");

        service.saveCurrentPriceFromKis("005930");

        verify(stockPriceRepository).save(priceCaptor.capture());
        StockPrice savedPrice = priceCaptor.getValue();
        assertThat(savedPrice.getTradeDate())
                .isEqualTo(LocalDate.of(2026, 8, 14));
        assertThat(savedPrice.getCollectedAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 14, 12, 0));
    }

    @Test
    void keepsRequestedTradeDateAndAddsKoreaCollectionTime() {
        StockPriceService service = serviceAt("2026-08-14T03:00:00Z");
        StockPriceRequest request = org.mockito.Mockito.mock(StockPriceRequest.class);
        when(request.getStockCode()).thenReturn("005930");
        when(request.getCurrentPrice()).thenReturn(71_000L);
        when(request.getChangeRate()).thenReturn(1.25);
        when(request.getVolume()).thenReturn(12_345_678L);
        when(request.getTradeDate()).thenReturn(LocalDate.of(2026, 8, 13));
        when(stockRepository.existsByStockCode("005930")).thenReturn(true);

        StockPriceResponse response = service.savePrice(request);

        verify(stockPriceRepository).save(priceCaptor.capture());
        assertThat(priceCaptor.getValue().getTradeDate())
                .isEqualTo(LocalDate.of(2026, 8, 13));
        assertThat(priceCaptor.getValue().getCollectedAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 14, 12, 0));
        assertThat(response.getTradeDate()).isEqualTo(LocalDate.of(2026, 8, 13));
        assertThat(response.getCollectedAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 14, 12, 0));
    }

    @Test
    void prePersistDoesNotOverwriteExplicitCollectionTime() {
        LocalDateTime collectedAt = LocalDateTime.of(2026, 8, 14, 12, 0);
        StockPrice stockPrice = StockPrice.builder()
                .stockCode("005930")
                .currentPrice(71_000L)
                .changeRate(1.25)
                .volume(12_345_678L)
                .tradeDate(LocalDate.of(2026, 8, 14))
                .collectedAt(collectedAt)
                .build();

        stockPrice.prePersist();

        assertThat(stockPrice.getCollectedAt()).isEqualTo(collectedAt);
    }

    private StockPriceService serviceAt(String instant) {
        when(stockPriceRepository.save(any(StockPrice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return new StockPriceService(
                stockRepository,
                stockPriceRepository,
                kisStockClient,
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    private void prepareKisPrice(String stockCode) {
        KisStockPriceResponse response =
                org.mockito.Mockito.mock(KisStockPriceResponse.class);
        KisStockPriceResponse.Output output =
                org.mockito.Mockito.mock(KisStockPriceResponse.Output.class);
        when(stockRepository.existsByStockCode(stockCode)).thenReturn(true);
        when(kisStockClient.getCurrentPrice(stockCode)).thenReturn(response);
        when(response.getRtCd()).thenReturn("0");
        when(response.getOutput()).thenReturn(output);
        when(output.getCurrentPrice()).thenReturn("71000");
        when(output.getChangeRate()).thenReturn("1.25");
        when(output.getVolume()).thenReturn("12345678");
    }
}
