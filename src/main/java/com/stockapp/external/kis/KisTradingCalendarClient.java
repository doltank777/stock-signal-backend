package com.stockapp.external.kis;

import com.stockapp.external.kis.dto.KisTradingCalendarResponse;
import com.stockapp.external.kis.dto.KisTradingDay;
import com.stockapp.global.config.KisProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class KisTradingCalendarClient {

    private static final String API_PATH =
            "/uapi/domestic-stock/v1/quotations/chk-holiday";
    private static final String TR_ID = "CTCA0903R";
    private static final String RATE_LIMIT_CODE = "EGW00201";
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.BASIC_ISO_DATE;

    private final KisProperties properties;
    private final KisTradingCalendarAccessTokenProvider accessTokenProvider;
    private final RestClient.Builder restClientBuilder;
    private final KisTradingCalendarSleeper sleeper;

    public List<KisTradingDay> getTradingDays(LocalDate baseDate) {
        return getTradingDaysWithDiagnostics(baseDate).days();
    }

    public KisTradingCalendarFetchResult getTradingDaysWithDiagnostics(
            LocalDate baseDate
    ) {
        return fetch(baseDate, null);
    }

    public List<KisTradingDay> getTradingDays(
            LocalDate startDate,
            LocalDate endDate
    ) {
        return getTradingDaysWithDiagnostics(startDate, endDate).days();
    }

    public KisTradingCalendarFetchResult getTradingDaysWithDiagnostics(
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (endDate == null) {
            throw new IllegalArgumentException("endDate is required");
        }
        if (startDate == null) {
            throw new IllegalArgumentException("startDate is required");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                    "endDate must be on or after startDate");
        }
        return fetch(startDate, endDate);
    }

    private KisTradingCalendarFetchResult fetch(
            LocalDate baseDate,
            LocalDate endDate
    ) {
        if (baseDate == null) {
            throw new IllegalArgumentException("baseDate is required");
        }
        KisProperties.TradingCalendar calendar = properties.getTradingCalendar();
        calendar.validateConfigured();
        String token = accessTokenProvider.getAccessToken();
        RestClient client = restClientBuilder.baseUrl(calendar.getBaseUrl())
                .build();
        List<KisTradingDay> result = new ArrayList<>();
        List<KisTradingCalendarPage> pages = new ArrayList<>();
        Set<String> continuationKeys = new HashSet<>();
        String fk = "";
        String nk = "";
        int apiCallCount = 0;

        int maxPages = calendar.getMaxPages();
        for (int page = 0; page < maxPages; page++) {
            if (page > 0) {
                sleep(calendar.getRequestIntervalMs());
            }
            String requestFk = fk;
            String requestNk = nk;
            PageResponse pageResponse = requestPageWithRateLimitRetry(
                    client, calendar, token, baseDate, requestFk, requestNk,
                    page + 1);
            apiCallCount += pageResponse.attemptCount();
            ResponseEntity<KisTradingCalendarResponse> entity =
                    pageResponse.entity();
            KisTradingCalendarResponse response = entity.getBody();
            if (response == null) {
                throw new IllegalArgumentException(
                        "KIS trading calendar response is missing");
            }
            List<KisTradingDay> pageDays = response.toTradingDays();
            result.addAll(pageDays);
            String trCont = entity.getHeaders().getFirst("tr_cont");
            pages.add(KisTradingCalendarPage.from(
                    page + 1,
                    pageResponse.attemptCount(),
                    trCont,
                    response.getContextAreaFk() != null
                            && !response.getContextAreaFk().isBlank(),
                    response.getContextAreaNk() != null
                            && !response.getContextAreaNk().isBlank(),
                    pageDays,
                    response.outputFieldNames()));
            boolean sourceHasMore = "M".equals(trCont) || "F".equals(trCont);
            if (endDate != null && maximumDate(result) != null
                    && !maximumDate(result).isBefore(endDate)) {
                return completeRange(baseDate, endDate, result, pages,
                        apiCallCount, !sourceHasMore, sourceHasMore);
            }
            if (!sourceHasMore) {
                if (endDate != null) {
                    throw incompleteRange(baseDate, endDate, result, pages,
                            apiCallCount, true, false);
                }
                return new KisTradingCalendarFetchResult(
                        result, pages, apiCallCount, null, null, false,
                        true, false);
            }
            fk = requireContinuation("ctx_area_fk", response.getContextAreaFk());
            nk = requireContinuation("ctx_area_nk", response.getContextAreaNk());
            if (!continuationKeys.add(fk + "\u0000" + nk)) {
                throw new IllegalArgumentException(
                        "KIS trading calendar pagination did not progress");
            }
            if (pages.size() >= maxPages) {
                KisTradingCalendarFetchResult partialResult =
                        diagnosticResult(baseDate, endDate, result, pages,
                                apiCallCount, false, true);
                throw new KisTradingCalendarPaginationLimitException(
                        maxPages, partialResult);
            }
        }
        throw new IllegalStateException("unreachable Calendar pagination state");
    }

    private KisTradingCalendarFetchResult completeRange(
            LocalDate startDate,
            LocalDate endDate,
            List<KisTradingDay> collected,
            List<KisTradingCalendarPage> pages,
            int apiCallCount,
            boolean sourcePaginationComplete,
            boolean sourceHasMore
    ) {
        Map<LocalDate, KisTradingDay> byDate = new LinkedHashMap<>();
        for (KisTradingDay day : collected) {
            KisTradingDay previous = byDate.putIfAbsent(day.tradeDate(), day);
            if (previous != null) {
                throw incompleteRange(startDate, endDate, collected, pages,
                        apiCallCount, sourcePaginationComplete, sourceHasMore,
                        "duplicate date: " + day.tradeDate());
            }
        }
        List<KisTradingDay> rangeDays = byDate.values().stream()
                .filter(day -> !day.tradeDate().isBefore(startDate)
                        && !day.tradeDate().isAfter(endDate))
                .toList();
        for (LocalDate date = startDate; !date.isAfter(endDate);
             date = date.plusDays(1)) {
            if (!byDate.containsKey(date)) {
                throw incompleteRange(startDate, endDate, collected, pages,
                        apiCallCount, sourcePaginationComplete, sourceHasMore,
                        "missing calendar date: " + date);
            }
        }
        return new KisTradingCalendarFetchResult(
                rangeDays, pages, apiCallCount, startDate, endDate, true,
                sourcePaginationComplete, sourceHasMore);
    }

    private KisTradingCalendarRangeIncompleteException incompleteRange(
            LocalDate startDate, LocalDate endDate,
            List<KisTradingDay> collected, List<KisTradingCalendarPage> pages,
            int apiCallCount, boolean sourcePaginationComplete,
            boolean sourceHasMore
    ) {
        return incompleteRange(startDate, endDate, collected, pages,
                apiCallCount, sourcePaginationComplete, sourceHasMore,
                "source pagination ended before requested end date");
    }

    private KisTradingCalendarRangeIncompleteException incompleteRange(
            LocalDate startDate, LocalDate endDate,
            List<KisTradingDay> collected, List<KisTradingCalendarPage> pages,
            int apiCallCount, boolean sourcePaginationComplete,
            boolean sourceHasMore, String reason
    ) {
        return new KisTradingCalendarRangeIncompleteException(
                "KIS trading calendar range incomplete: " + reason,
                diagnosticResult(startDate, endDate, collected, pages,
                        apiCallCount, sourcePaginationComplete, sourceHasMore));
    }

    private KisTradingCalendarFetchResult diagnosticResult(
            LocalDate startDate, LocalDate endDate,
            List<KisTradingDay> collected, List<KisTradingCalendarPage> pages,
            int apiCallCount, boolean sourcePaginationComplete,
            boolean sourceHasMore
    ) {
        return new KisTradingCalendarFetchResult(
                collected, pages, apiCallCount, startDate, endDate, false,
                sourcePaginationComplete, sourceHasMore);
    }

    private LocalDate maximumDate(List<KisTradingDay> days) {
        return days.stream().map(KisTradingDay::tradeDate)
                .max(LocalDate::compareTo).orElse(null);
    }

    private PageResponse requestPageWithRateLimitRetry(
            RestClient client,
            KisProperties.TradingCalendar calendar,
            String token,
            LocalDate baseDate,
            String fk,
            String nk,
            int pageNumber
    ) {
        int maxAttempts = calendar.getRateLimitRetryMaxAttempts();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                RestClient.RequestHeadersSpec<?> request = client.get()
                        .uri(builder -> builder.path(API_PATH)
                                .queryParam("BASS_DT", baseDate.format(FORMATTER))
                                .queryParam("CTX_AREA_FK", fk)
                                .queryParam("CTX_AREA_NK", nk)
                                .build())
                        .header("authorization", "Bearer " + token)
                        .header("appkey", calendar.getAppKey())
                        .header("appsecret", calendar.getAppSecret())
                        .header("tr_id", TR_ID);
                if (pageNumber > 1) {
                    request = request.header("tr_cont", "N");
                }
                ResponseEntity<KisTradingCalendarResponse> entity = request
                        .retrieve().toEntity(KisTradingCalendarResponse.class);
                KisTradingCalendarResponse response = entity.getBody();
                if (response != null && !"0".equals(response.getRtCd())) {
                    if (RATE_LIMIT_CODE.equals(response.getMsgCd())) {
                        if (attempt < maxAttempts) {
                            logRateLimitRetry(pageNumber, attempt);
                            sleep(calendar.getRateLimitRetryDelayMs());
                            continue;
                        }
                    }
                    throw new KisApiException(
                            response.getMsgCd(), response.getMessage());
                }
                return new PageResponse(entity, attempt);
            } catch (RestClientResponseException exception) {
                KisTradingCalendarResponse error = errorResponse(exception);
                if (error == null || !RATE_LIMIT_CODE.equals(error.getMsgCd())) {
                    throw exception;
                }
                if (attempt >= maxAttempts) {
                    throw new KisApiException(
                            error.getMsgCd(), error.getMessage());
                }
                logRateLimitRetry(pageNumber, attempt);
                sleep(calendar.getRateLimitRetryDelayMs());
            }
        }
        throw new IllegalStateException("unreachable Calendar retry state");
    }

    private KisTradingCalendarResponse errorResponse(
            RestClientResponseException exception
    ) {
        try {
            return exception.getResponseBodyAs(
                    KisTradingCalendarResponse.class);
        } catch (RuntimeException parsingFailure) {
            return null;
        }
    }

    private void logRateLimitRetry(int pageNumber, int attempt) {
        log.warn("KIS Trading Calendar rate limited; retrying page={} after attempt={}",
                pageNumber, attempt);
    }

    private void sleep(long milliseconds) {
        if (milliseconds <= 0) {
            return;
        }
        try {
            sleeper.sleep(milliseconds);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new KisTradingCalendarInterruptedException(exception);
        }
    }

    private record PageResponse(
            ResponseEntity<KisTradingCalendarResponse> entity,
            int attemptCount
    ) {
    }

    private String requireContinuation(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "KIS trading calendar " + name + " is required");
        }
        return value;
    }
}
