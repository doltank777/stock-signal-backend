package com.stockapp.domain.stock;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyPriceUpdateRunnerTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 14);

    @Test
    void usesDedicatedProfile() {
        Profile profile = DailyPriceUpdateRunner.class.getAnnotation(Profile.class);
        assertThat(profile.value()).containsExactly("daily-price-update");
    }

    @Test
    void runsFullUpdateWithDefaultDate() {
        DailyPriceUpdateService service = mock(DailyPriceUpdateService.class);
        DailyPriceUpdateRunner runner = new DailyPriceUpdateRunner(service, "", "");

        runner.run(null);

        verify(service).update();
    }

    @Test
    void runsFullUpdateWithConfiguredDate() {
        DailyPriceUpdateService service = mock(DailyPriceUpdateService.class);
        DailyPriceUpdateRunner runner = new DailyPriceUpdateRunner(
                service, " 2026-08-14 ", " ");

        runner.run(null);

        verify(service).update(BASE_DATE);
    }

    @Test
    void trimsStockCodeAndRunsSelectedStockWithDate() {
        DailyPriceUpdateService service = mock(DailyPriceUpdateService.class);
        DailyPriceUpdateRunner runner = new DailyPriceUpdateRunner(
                service, "2026-08-14", " 005930 ");

        runner.run(null);

        verify(service).updateStock("005930", BASE_DATE);
        verify(service, never()).update(BASE_DATE);
    }

    @Test
    void runsSelectedStockWithDefaultDate() {
        DailyPriceUpdateService service = mock(DailyPriceUpdateService.class);
        DailyPriceUpdateRunner runner = new DailyPriceUpdateRunner(
                service, "", " 005930 ");

        runner.run(null);

        verify(service).updateStock("005930");
    }

    @Test
    void propagatesSelectedStockFailure() {
        DailyPriceUpdateService service = mock(DailyPriceUpdateService.class);
        IllegalArgumentException failure = new IllegalArgumentException("not found");
        when(service.updateStock("123456", BASE_DATE)).thenThrow(failure);
        DailyPriceUpdateRunner runner = new DailyPriceUpdateRunner(
                service, BASE_DATE.toString(), "123456");

        assertThatThrownBy(() -> runner.run(null)).isSameAs(failure);
    }
}
