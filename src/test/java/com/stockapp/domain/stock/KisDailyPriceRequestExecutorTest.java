package com.stockapp.domain.stock;

import com.stockapp.external.kis.KisApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class KisDailyPriceRequestExecutorTest {

    @Mock
    private DailyPriceLoadSleeper sleeper;

    private KisDailyPriceRequestExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new KisDailyPriceRequestExecutor(sleeper);
    }

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    @Test
    void returnsValueAndOneAttemptOnFirstSuccess() {
        AtomicInteger attempts = new AtomicInteger();

        KisDailyPriceRequestExecution<String> result = executor.execute(
                policy(3, 2_000, 2), () -> "success",
                attempts::incrementAndGet);

        assertThat(result.value()).isEqualTo("success");
        assertThat(result.attemptCount()).isOne();
        assertThat(attempts).hasValue(1);
        verifyNoInteractions(sleeper);
    }

    @Test
    void retriesRateLimitAndReturnsActualAttemptCount() throws Exception {
        AtomicInteger calls = new AtomicInteger();

        KisDailyPriceRequestExecution<String> result = executor.execute(
                policy(3, 2_000, 2), failThenSucceed(calls,
                        new KisApiException("EGW00201", "rate limit")),
                () -> { });

        assertThat(result.attemptCount()).isEqualTo(2);
        verify(sleeper).sleep(2_000);
    }

    @Test
    void retriesHttp429ServerErrorAndNetworkFailure() {
        List<RuntimeException> failures = List.of(
                new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS),
                new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR),
                new ResourceAccessException("network"));

        for (RuntimeException failure : failures) {
            AtomicInteger calls = new AtomicInteger();
            assertThat(executor.execute(policy(2, 0, 2),
                    failThenSucceed(calls, failure), () -> { }).attemptCount())
                    .isEqualTo(2);
        }
    }

    @Test
    void reportsAttemptCountWhenRetryIsExhausted() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> executor.execute(policy(3, 0, 2),
                () -> { throw new ResourceAccessException("network"); },
                attempts::incrementAndGet))
                .isInstanceOfSatisfying(
                        KisDailyPriceRequestExhaustedException.class,
                        exception -> assertThat(exception.getAttemptCount())
                                .isEqualTo(3));
        assertThat(attempts).hasValue(3);
    }

    @Test
    void propagatesAuthenticationAndOtherNonRetryableFailuresImmediately() {
        List<RuntimeException> failures = List.of(
                new HttpClientErrorException(HttpStatus.UNAUTHORIZED),
                new HttpClientErrorException(HttpStatus.FORBIDDEN),
                new KisApiException("OTHER", "business"),
                new IllegalArgumentException("malformed"));

        for (RuntimeException failure : failures) {
            AtomicInteger attempts = new AtomicInteger();
            assertThatThrownBy(() -> executor.execute(policy(3, 0, 2),
                    () -> { throw failure; }, attempts::incrementAndGet))
                    .isSameAs(failure);
            assertThat(attempts).hasValue(1);
        }
    }

    @Test
    void restoresInterruptAndStopsDuringBackoff() throws Exception {
        doThrow(new InterruptedException("stop")).when(sleeper).sleep(100);

        assertThatThrownBy(() -> executor.execute(policy(3, 100, 2),
                () -> { throw new ResourceAccessException("network"); },
                () -> { }))
                .isInstanceOf(KisDailyPriceRequestInterruptedException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void usesConfiguredExponentialBackoffSequence() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            delays.add(invocation.getArgument(0));
            return null;
        }).when(sleeper).sleep(org.mockito.ArgumentMatchers.anyLong());

        assertThatThrownBy(() -> executor.execute(policy(4, 2_000, 2),
                () -> {
                    calls.incrementAndGet();
                    throw new ResourceAccessException("network");
                }, () -> { }))
                .isInstanceOf(KisDailyPriceRequestExhaustedException.class);
        assertThat(calls).hasValue(4);
        assertThat(delays).containsExactly(2_000L, 4_000L, 8_000L);
    }

    @Test
    void rejectsInvalidAndOverflowingPolicy() {
        assertThatThrownBy(() -> policy(0, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy(1, -1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy(1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy(4, Long.MAX_VALUE, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("long range");
    }

    private KisDailyPriceRequestPolicy policy(
            int attempts, long delay, long multiplier) {
        return new KisDailyPriceRequestPolicy(attempts, delay, multiplier);
    }

    private Supplier<String> failThenSucceed(
            AtomicInteger calls, RuntimeException failure) {
        return () -> {
            if (calls.incrementAndGet() == 1) throw failure;
            return "success";
        };
    }
}
