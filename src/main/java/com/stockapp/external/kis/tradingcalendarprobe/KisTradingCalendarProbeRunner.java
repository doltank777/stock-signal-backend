package com.stockapp.external.kis.tradingcalendarprobe;

import com.stockapp.external.kis.KisApiException;
import com.stockapp.external.kis.KisTradingCalendarClient;
import com.stockapp.external.kis.KisTradingCalendarFetchResult;
import com.stockapp.external.kis.KisTradingCalendarPage;
import com.stockapp.external.kis.KisTradingCalendarPaginationLimitException;
import com.stockapp.external.kis.KisTradingCalendarResponseOrder;
import com.stockapp.external.kis.dto.KisTradingDay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

@Slf4j
@RequiredArgsConstructor
public class KisTradingCalendarProbeRunner implements ApplicationRunner {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final KisTradingCalendarProbeProperties properties;
    private final KisTradingCalendarClient client;
    private final Clock clock;

    @Override
    public void run(ApplicationArguments args) {
        execute();
    }

    KisTradingCalendarProbeResult execute() {
        LocalDate baseDate = properties.resolvedBaseDate(clock);
        LocalDate endDate = properties.resolvedEndDate(baseDate);
        int maxPrintRows = properties.validatedMaxPrintRows();
        OffsetDateTime requestedAt = OffsetDateTime.now(
                clock.withZone(KOREA_ZONE));

        KisTradingCalendarFetchResult fetchResult;
        try {
            fetchResult = endDate == null
                    ? client.getTradingDaysWithDiagnostics(baseDate)
                    : client.getTradingDaysWithDiagnostics(baseDate, endDate);
        } catch (KisTradingCalendarPaginationLimitException exception) {
            KisTradingCalendarProbeResult partial = summarize(
                    baseDate, requestedAt, exception.getPartialFetchResult(),
                    false);
            logResult(partial, maxPrintRows, exception);
            throw exception;
        } catch (KisApiException exception) {
            log.error("KIS trading calendar probe business error - msgCd: {}, msg1: {}",
                    exception.getMessageCode(), exception.getMessage());
            if ("EGW02006".equals(exception.getMessageCode())) {
                log.error("Calendar API appears unsupported by the configured KIS environment; configure the real-trading Calendar REST environment");
            }
            throw exception;
        } catch (RestClientResponseException exception) {
            log.error("KIS trading calendar probe HTTP error - status: {}, message: {}",
                    exception.getStatusCode().value(), exception.getMessage());
            throw exception;
        }

        KisTradingCalendarProbeResult result = summarize(
                baseDate, requestedAt, fetchResult,
                fetchResult.sourcePaginationComplete());
        logResult(result, maxPrintRows, null);
        return result;
    }

    KisTradingCalendarProbeResult summarize(
            LocalDate baseDate,
            OffsetDateTime requestedAt,
            KisTradingCalendarFetchResult fetchResult,
            boolean paginationComplete
    ) {
        List<KisTradingDay> rows = fetchResult.days();
        LocalDate firstCollectedDate = rows.isEmpty()
                ? null : rows.getFirst().tradeDate();
        LocalDate lastCollectedDate = rows.isEmpty()
                ? null : rows.getLast().tradeDate();
        LocalDate minDate = rows.stream().map(KisTradingDay::tradeDate)
                .min(Comparator.naturalOrder()).orElse(null);
        LocalDate maxDate = rows.stream().map(KisTradingDay::tradeDate)
                .max(Comparator.naturalOrder()).orElse(null);
        long tradingDays = rows.stream().filter(KisTradingDay::tradingDay).count();
        long saturdayRows = rows.stream().filter(row ->
                row.tradeDate().getDayOfWeek() == DayOfWeek.SATURDAY).count();
        long sundayRows = rows.stream().filter(row ->
                row.tradeDate().getDayOfWeek() == DayOfWeek.SUNDAY).count();
        Set<String> fieldNames = new LinkedHashSet<>();
        fetchResult.pages().forEach(page ->
                fieldNames.addAll(page.outputFieldNames()));

        long expectedDateCount = minDate == null ? 0
                : ChronoUnit.DAYS.between(minDate, maxDate) + 1;
        long distinctDateCount = new HashSet<>(rows.stream()
                .map(KisTradingDay::tradeDate).toList()).size();

        return new KisTradingCalendarProbeResult(
                baseDate, fetchResult.requestedEndDate(), requestedAt,
                paginationComplete, fetchResult.requestedRangeComplete(),
                fetchResult.sourceHasMore(), rows,
                fetchResult.pages(), firstCollectedDate, lastCollectedDate,
                minDate, maxDate, KisTradingCalendarResponseOrder.from(rows),
                distinctDateCount, rows.size() - distinctDateCount,
                expectedDateCount,
                rows.stream().anyMatch(row -> row.tradeDate().equals(baseDate)),
                tradingDays, rows.size() - tradingDays,
                saturdayRows + sundayRows, saturdayRows, sundayRows,
                saturdayRows > 0, sundayRows > 0,
                rows.stream().filter(row -> row.tradeDate().isAfter(baseDate)).count(),
                rows.stream().filter(row -> row.tradeDate().isBefore(baseDate)).count(),
                expectedDateCount - distinctDateCount,
                fetchResult.apiCallCount(),
                fieldNames);
    }

    private void logResult(
            KisTradingCalendarProbeResult result,
            int maxPrintRows,
            KisTradingCalendarPaginationLimitException limitException
    ) {
        String label = result.paginationComplete()
                ? "[KIS TRADING CALENDAR PROBE]"
                : "[KIS TRADING CALENDAR PROBE - PARTIAL]";
        log.info("{}\n\nmode={}\nsourcePaginationComplete={}"
                        + "\nrequestedStartDate={}\nrequestedEndDate={}"
                        + "\nrequestedRangeComplete={}\nsourceHasMore={}\nrequestedAt={}"
                        + "\nrowCount={}\npageCount={}\napiCallCount={}"
                        + "\nuniqueDateCount={}\nduplicateDateCount={}"
                        + "\nexpectedCalendarDateCount={}"
                        + "\n\nfirstCollectedDate={}\nlastCollectedDate={}"
                        + "\nminCollectedDate={}\nmaxCollectedDate={}"
                        + "\noverallResponseOrder={}\nbaseDatePresent={}"
                        + "\n\ntradingDayCount={}\nclosedDayCount={}"
                        + "\nweekendRowCount={}\ncontainsSaturday={}"
                        + "\ncontainsSunday={}\nfutureDateCount={}\npastDateCount={}"
                        + "\nsaturdayRowCount={}\nsundayRowCount={}"
                        + "\nmissingCalendarDateCount={}"
                        + "\noutputFieldNames={}",
                label, result.mode(), result.paginationComplete(),
                result.baseDate(), result.requestedEndDate(),
                result.requestedRangeComplete(), result.sourceHasMore(),
                result.requestedAt(), result.rowCount(), result.pageCount(),
                result.apiCallCount(), result.uniqueDateCount(),
                result.duplicateDateCount(), result.expectedCalendarDateCount(),
                result.firstCollectedDate(), result.lastCollectedDate(),
                result.minCollectedDate(), result.maxCollectedDate(),
                result.overallResponseOrder(), result.baseDatePresent(),
                result.tradingDayCount(), result.closedDayCount(),
                result.weekendRowCount(), result.containsSaturday(),
                result.containsSunday(), result.futureDateCount(),
                result.pastDateCount(), result.saturdayRowCount(),
                result.sundayRowCount(), result.missingCalendarDateCount(),
                result.outputFieldNames());

        if (limitException != null) {
            log.info("paginationLimit maxPages={} completedPages={} apiCallCount={} collectedRowCount={}",
                    limitException.getMaxPages(),
                    limitException.getCompletedPages(),
                    limitException.getApiCallCount(), result.rowCount());
        }

        if (!result.paginationComplete() || properties.isLogPageSummary()) {
            result.pages().forEach(this::logPage);
        }
        int printCount = properties.isPrintAllRows()
                ? result.rowCount() : Math.min(maxPrintRows, result.rowCount());
        for (int index = 0; index < printCount; index++) {
            KisTradingDay row = result.rows().get(index);
            log.info("calendarRow index={} tradeDate={} tradingDay={}",
                    index + 1, row.tradeDate(), row.tradingDay());
        }
        if (printCount < result.rowCount()) {
            log.info("calendarRows truncated printed={} omitted={} (set print-all-rows=true to print all)",
                    printCount, result.rowCount() - printCount);
        }
    }

    private void logPage(KisTradingCalendarPage page) {
        log.info("calendarPage page={} attempts={} retryCount={} rowCount={} firstDate={} lastDate={}"
                        + " minDate={} maxDate={} order={} trCont={} continuationFkPresent={}"
                        + " continuationNkPresent={} outputFieldNames={}",
                page.pageNumber(), page.attemptCount(),
                page.attemptCount() - 1, page.rowCount(), page.firstDate(),
                page.lastDate(), page.minDate(), page.maxDate(),
                page.responseOrder(), page.trCont(),
                page.contextAreaFkPresent(), page.contextAreaNkPresent(),
                page.outputFieldNames());
    }
}
