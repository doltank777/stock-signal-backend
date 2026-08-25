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
    private final KisDailyPriceProbeAnalyzer analyzer;

    @Override
    public void run(ApplicationArguments args) {
        execute();
    }

    KisDailyPriceProbeResult execute() {
        String stockCode = properties.requiredStockCode();
        KisDailyPriceProbeRequest request = properties.resolvedRequest(clock);
        OffsetDateTime requestedAt = OffsetDateTime.now(clock.withZone(KOREA_ZONE));

        List<KisDailyPrice> response;
        try {
            response = dailyPriceClient.getDailyPrices(
                    stockCode, request.startDate(), request.endDate());
        } catch (KisApiException exception) {
            log.error("KIS daily price probe business error - msgCd: {}, msg1: {}",
                    exception.getMessageCode(), exception.getMessage());
            throw exception;
        } catch (RestClientResponseException exception) {
            log.error("KIS daily price probe HTTP error - status: {}, message: {}",
                    exception.getStatusCode().value(), exception.getMessage());
            throw exception;
        }
        KisDailyPrice row = request.singleDateMode()
                ? response.stream()
                        .filter(price -> request.targetDate().equals(price.getTradeDate()))
                        .findFirst()
                        .orElse(null)
                : null;
        KisDailyPriceProbeAnalysis analysis = analyzer.analyze(
                response, request.startDate(), request.endDate());

        KisDailyPriceProbeResult result = new KisDailyPriceProbeResult(
                stockCode,
                request.targetDate(),
                request.startDate(),
                request.endDate(),
                requestedAt,
                row,
                analysis);
        logResult(result);
        return result;
    }

    private void logResult(KisDailyPriceProbeResult result) {
        if (result.targetDate() == null) {
            logRangeResult(result);
            return;
        }
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

    private void logRangeResult(KisDailyPriceProbeResult result) {
        KisDailyPriceProbeAnalysis analysis = result.analysis();
        log.info("[KIS DAILY PRICE RANGE PROBE]"
                        + "\n\nstockCode={}\nrequestedStartDate={}\nrequestedEndDate={}"
                        + "\nrequestedAt={}\n\nresponseRowCount={}"
                        + "\nearliestResponseDate={}\nlatestResponseDate={}"
                        + "\nresponseOrder={}\nduplicateDateCount={}"
                        + "\noutOfRangeDateCount={}\nlimitReached={}"
                        + "\nlimitReachedMeaning=responseRowCountEqualsOfficialLimitNotCompleteness"
                        + "\nexactStartDatePresent={}\nexactEndDatePresent={}"
                        + "\nfirstResponseDates={}\nlastResponseDates={}",
                result.stockCode(), result.requestedStartDate(),
                result.requestedEndDate(), result.requestedAt(),
                analysis.responseRowCount(), analysis.earliestResponseDate(),
                analysis.latestResponseDate(), analysis.responseOrder(),
                analysis.duplicateDateCount(), analysis.outOfRangeDateCount(),
                analysis.limitReached(), analysis.exactStartDatePresent(),
                analysis.exactEndDatePresent(), analysis.firstResponseDates(),
                analysis.lastResponseDates());
    }
}
