package com.stockapp.domain.stock;

import com.stockapp.domain.screening.realtime.KrxRegularMarketSessionPolicy;
import com.stockapp.domain.screening.realtime.OperationalRealtimeAutomationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StockPriceSchedulerTest {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 24);

    @Test
    void collectsPricesOnTradingDayDuringMarketHours() {
        StockRepository repository = mock(StockRepository.class);
        StockPriceService service = mock(StockPriceService.class);
        KrxTradingCalendar calendar = mock(KrxTradingCalendar.class);
        Stock stock = Stock.builder().stockCode("005930").build();
        when(calendar.isTradingDay(MONDAY)).thenReturn(true);
        when(repository.findAll()).thenReturn(List.of(stock));
        StockPriceScheduler scheduler = schedulerAt(
                LocalDateTime.of(2026, 8, 24, 10, 0),
                repository, service, calendar);

        scheduler.collectStockPrices();

        verify(calendar).isTradingDay(MONDAY);
        verify(repository).findAll();
        verify(service).saveCurrentPriceFromKis("005930");
    }

    @Test
    void skipsClosedDayBeforeStockLookupWithoutFallback() {
        StockRepository repository = mock(StockRepository.class);
        StockPriceService service = mock(StockPriceService.class);
        KrxTradingCalendar calendar = mock(KrxTradingCalendar.class);
        when(calendar.isTradingDay(MONDAY)).thenReturn(false);
        StockPriceScheduler scheduler = schedulerAt(
                LocalDateTime.of(2026, 8, 24, 10, 0),
                repository, service, calendar);

        scheduler.collectStockPrices();

        verify(calendar).isTradingDay(MONDAY);
        verify(calendar, never()).previousTradingDay(MONDAY);
        verifyNoInteractions(repository, service);
    }

    @Test
    void failsClosedWhenCalendarIsUnavailable() {
        StockRepository repository = mock(StockRepository.class);
        StockPriceService service = mock(StockPriceService.class);
        KrxTradingCalendar calendar = mock(KrxTradingCalendar.class);
        when(calendar.isTradingDay(MONDAY)).thenThrow(
                new TradingCalendarUnavailableException(
                        MONDAY, "coverage gap"));
        StockPriceScheduler scheduler = schedulerAt(
                LocalDateTime.of(2026, 8, 24, 10, 0),
                repository, service, calendar);

        scheduler.collectStockPrices();

        verify(calendar).isTradingDay(MONDAY);
        verify(calendar, never()).previousTradingDay(MONDAY);
        verifyNoInteractions(repository, service);
    }

    @Test
    void skipsBeforeMarketOpenWithoutCalendarLookup() {
        assertOutsideMarketSkips(LocalDateTime.of(2026, 8, 24, 8, 59));
    }

    @Test
    void includesMarketOpenBoundary() {
        assertMarketBoundaryCollects(LocalDateTime.of(2026, 8, 24, 9, 0));
    }

    @Test
    void includesMarketCloseBoundary() {
        assertMarketBoundaryCollects(LocalDateTime.of(2026, 8, 24, 15, 30));
    }

    @Test
    void skipsAfterMarketCloseWithoutCalendarLookup() {
        assertOutsideMarketSkips(LocalDateTime.of(2026, 8, 24, 15, 31));
    }

    @Test
    void skipsWeekendBeforeCalendarLookup() {
        StockRepository repository = mock(StockRepository.class);
        StockPriceService service = mock(StockPriceService.class);
        KrxTradingCalendar calendar = mock(KrxTradingCalendar.class);
        when(calendar.isTradingDay(LocalDate.of(2026, 8, 23)))
                .thenReturn(false);
        StockPriceScheduler scheduler = schedulerAt(
                LocalDateTime.of(2026, 8, 23, 10, 0),
                repository, service, calendar);

        scheduler.collectStockPrices();

        verify(calendar).isTradingDay(LocalDate.of(2026, 8, 23));
        verifyNoInteractions(repository, service);
    }

    @Test
    void keepsConfiguredCronAndKoreaTimezone() throws Exception {
        Method method = StockPriceScheduler.class
                .getDeclaredMethod("collectStockPrices");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("0 0 9-16 * * MON-FRI");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }

    private void assertMarketBoundaryCollects(LocalDateTime koreaDateTime) {
        StockRepository repository = mock(StockRepository.class);
        StockPriceService service = mock(StockPriceService.class);
        KrxTradingCalendar calendar = mock(KrxTradingCalendar.class);
        when(calendar.isTradingDay(MONDAY)).thenReturn(true);
        when(repository.findAll()).thenReturn(List.of());
        StockPriceScheduler scheduler = schedulerAt(
                koreaDateTime, repository, service, calendar);

        scheduler.collectStockPrices();

        verify(calendar).isTradingDay(MONDAY);
        verify(repository).findAll();
        verifyNoInteractions(service);
    }

    private void assertOutsideMarketSkips(LocalDateTime koreaDateTime) {
        StockRepository repository = mock(StockRepository.class);
        StockPriceService service = mock(StockPriceService.class);
        KrxTradingCalendar calendar = mock(KrxTradingCalendar.class);
        StockPriceScheduler scheduler = schedulerAt(
                koreaDateTime, repository, service, calendar);

        scheduler.collectStockPrices();

        verifyNoInteractions(calendar, repository, service);
    }

    private StockPriceScheduler schedulerAt(
            LocalDateTime koreaDateTime,
            StockRepository repository,
            StockPriceService service,
            KrxTradingCalendar calendar) {
        Clock clock = Clock.fixed(
                koreaDateTime.atZone(KOREA_ZONE).toInstant(), KOREA_ZONE);
        var properties = new OperationalRealtimeAutomationProperties();
        properties.validate();
        return new StockPriceScheduler(repository, service, calendar,
                new KrxRegularMarketSessionPolicy(properties, clock));
    }
}
