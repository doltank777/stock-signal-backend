package com.stockapp.domain.stock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class DailyPriceFinalizationExecutionRepositoryTest {

    @Autowired
    DailyPriceFinalizationExecutionRepository repository;

    @Test
    void queriesByDateRunningAndLatestAndEnforcesUniqueDate() {
        LocalDate firstDate = LocalDate.of(2026, 8, 19);
        LocalDate secondDate = LocalDate.of(2026, 8, 20);
        repository.saveAndFlush(DailyPriceFinalizationExecution.create(
                firstDate, Instant.parse("2026-08-19T07:20:00Z")));
        repository.saveAndFlush(DailyPriceFinalizationExecution.create(
                secondDate, Instant.parse("2026-08-20T07:20:00Z")));

        assertThat(repository.findByTargetTradeDate(firstDate)).isPresent();
        assertThat(repository.findByStatusOrderByStartedAtAsc(
                DailyPriceFinalizationExecutionStatus.RUNNING))
                .extracting(DailyPriceFinalizationExecution::getTargetTradeDate)
                .containsExactly(firstDate, secondDate);
        assertThat(repository.findByReadyFalseOrderByStartedAtAsc())
                .hasSize(2);
        assertThat(repository.findFirstByOrderByStartedAtDesc())
                .get().extracting(DailyPriceFinalizationExecution::getTargetTradeDate)
                .isEqualTo(secondDate);

        assertThatThrownBy(() -> repository.saveAndFlush(
                DailyPriceFinalizationExecution.create(
                        firstDate, Instant.parse("2026-08-21T07:20:00Z"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
