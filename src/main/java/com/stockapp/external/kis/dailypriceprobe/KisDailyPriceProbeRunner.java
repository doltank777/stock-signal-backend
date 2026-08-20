package com.stockapp.external.kis.dailypriceprobe;

import com.stockapp.external.kis.KisDailyPriceClient;
import com.stockapp.external.kis.KisApiException;
import com.stockapp.external.kis.dto.KisDailyPrice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class KisDailyPriceProbeRunner implements ApplicationRunner {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final KisDailyPriceProbeProperties properties;
    private final KisDailyPriceClient dailyPriceClient;
    private final Clock clock;

    @Override
    public void run(ApplicationArguments args) {
        execute();
    }

    KisDailyPriceProbeResult execute() {
        String stockCode = properties.requiredStockCode();
        LocalDate targetDate = properties.resolvedTargetDate(clock);
        OffsetDateTime requestedAt = OffsetDateTime.now(clock.withZone(KOREA_ZONE));

        List<KisDailyPrice> response;
        try {
            response = dailyPriceClient.getDailyPrices(
                    stockCode, targetDate, targetDate);
        } catch (KisApiException exception) {
            log.error("KIS daily price probe business error - msgCd: {}, msg1: {}",
                    exception.getMessageCode(), exception.getMessage());
            throw exception;
        } catch (RestClientResponseException exception) {
            log.error("KIS daily price probe HTTP error - status: {}, message: {}",
                    exception.getStatusCode().value(), exception.getMessage());
            throw exception;
        }
        KisDailyPrice row = response.stream()
                .filter(price -> targetDate.equals(price.getTradeDate()))
                .findFirst()
                .orElse(null);

        KisDailyPriceProbeResult result = new KisDailyPriceProbeResult(
                stockCode, targetDate, requestedAt, response.size(), row);
        logResult(result);
        return result;
    }

    private void logResult(KisDailyPriceProbeResult result) {
        log.info("[KIS DAILY PRICE PROBE]\n\nstockCode={}\ntargetDate={}\nrequestedAt={}"
                        + "\n\nrowFound={}\nresponseRowCount={}",
                result.stockCode(), result.targetDate(), result.requestedAt(),
                result.rowFound(), result.responseRowCount());
        if (result.rowFound()) {
            KisDailyPrice row = result.row();
            log.info("tradeDate={}\nopen={}\nhigh={}\nlow={}\nclose={}\nvolume={}",
                    row.getTradeDate(), row.getOpenPrice(), row.getHighPrice(),
                    row.getLowPrice(), row.getClosePrice(), row.getVolume());
        }
    }
}
