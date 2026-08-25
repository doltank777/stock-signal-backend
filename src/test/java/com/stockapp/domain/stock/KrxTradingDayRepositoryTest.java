package com.stockapp.domain.stock;

import com.stockapp.external.kis.dto.KisTradingDay;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class KrxTradingDayRepositoryTest {

    @Autowired KrxTradingDayRepository repository;

    @Test
    void storesClosedDatesAndQueriesPreviousTradingDay() {
        Instant synchronizedAt = Instant.parse("2026-08-14T00:00:00Z");
        repository.saveAll(List.of(
                KrxTradingDay.create(LocalDate.of(2026, 8, 14), true,
                        "KIS", synchronizedAt),
                KrxTradingDay.create(LocalDate.of(2026, 8, 15), false,
                        "KIS", synchronizedAt),
                KrxTradingDay.create(LocalDate.of(2026, 8, 16), false,
                        "KIS", synchronizedAt),
                KrxTradingDay.create(LocalDate.of(2026, 8, 17), true,
                        "KIS", synchronizedAt)));
        repository.flush();

        assertThat(repository.findById(LocalDate.of(2026, 8, 15)))
                .get().extracting(KrxTradingDay::isTradingDay)
                .isEqualTo(false);
        assertThat(repository
                .findFirstByTradeDateBeforeAndTradingDayTrueOrderByTradeDateDesc(
                        LocalDate.of(2026, 8, 17)))
                .get().extracting(KrxTradingDay::getTradeDate)
                .isEqualTo(LocalDate.of(2026, 8, 14));
    }

    @Test
    void queriesOnlyLimitedTradingDaysBeforeDateInDescendingOrder() {
        Instant synchronizedAt = Instant.EPOCH;
        repository.saveAll(List.of(
                KrxTradingDay.create(LocalDate.of(2026, 8, 19), true,
                        "KIS", synchronizedAt),
                KrxTradingDay.create(LocalDate.of(2026, 8, 20), false,
                        "KIS", synchronizedAt),
                KrxTradingDay.create(LocalDate.of(2026, 8, 21), true,
                        "KIS", synchronizedAt),
                KrxTradingDay.create(LocalDate.of(2026, 8, 24), true,
                        "KIS", synchronizedAt)));
        repository.flush();

        assertThat(repository
                .findByTradeDateBeforeAndTradingDayTrueOrderByTradeDateDesc(
                        LocalDate.of(2026, 8, 24), PageRequest.of(0, 2)))
                .extracting(KrxTradingDay::getTradeDate)
                .containsExactly(
                        LocalDate.of(2026, 8, 21),
                        LocalDate.of(2026, 8, 19));
    }

    @Test
    void writerInsertsUpdatesAndRefreshesUnchangedSynchronizationTime() {
        KrxTradingCalendarWriter writer =
                new KrxTradingCalendarWriter(repository);
        LocalDate date = LocalDate.of(2026, 8, 14);
        Instant first = Instant.parse("2026-08-14T00:00:00Z");
        Instant second = Instant.parse("2026-08-15T00:00:00Z");

        assertThat(writer.write(List.of(
                new KisTradingDay(date, false)), first).inserted()).isOne();
        assertThat(writer.write(List.of(
                new KisTradingDay(date, true)), second).updated()).isOne();
        assertThat(writer.write(List.of(
                new KisTradingDay(date, true)), second.plusSeconds(1))
                .unchanged()).isOne();

        KrxTradingDay saved = repository.findById(date).orElseThrow();
        assertThat(saved.isTradingDay()).isTrue();
        assertThat(saved.getSynchronizedAt()).isEqualTo(second.plusSeconds(1));
    }
}
