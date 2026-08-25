package com.stockapp.domain.stock;

import com.stockapp.external.kis.KisApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Objects;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class KisDailyPriceRequestExecutor {
    private static final String RATE_LIMIT_CODE = "EGW00201";

    private final DailyPriceLoadSleeper sleeper;

    public <T> KisDailyPriceRequestExecution<T> execute(
            KisDailyPriceRequestPolicy policy,
            Supplier<T> request,
            Runnable attemptListener
    ) {
        Objects.requireNonNull(policy, "policy is required");
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(attemptListener, "attemptListener is required");

        long backoff = policy.initialRetryDelayMs();
        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            attemptListener.run();
            try {
                return new KisDailyPriceRequestExecution<>(request.get(), attempt);
            } catch (RuntimeException exception) {
                if (!isRetryable(exception)) throw exception;
                if (attempt == policy.maxAttempts()) {
                    throw new KisDailyPriceRequestExhaustedException(exception, attempt);
                }
                sleep(backoff);
                if (attempt < policy.maxAttempts() - 1) {
                    backoff = Math.multiplyExact(backoff, policy.backoffMultiplier());
                }
            }
        }
        throw new IllegalStateException("unreachable retry state");
    }

    private boolean isRetryable(RuntimeException exception) {
        if (exception instanceof KisApiException kisException) {
            return RATE_LIMIT_CODE.equals(kisException.getMessageCode());
        }
        if (exception instanceof ResourceAccessException) return true;
        if (exception instanceof RestClientResponseException responseException) {
            HttpStatusCode status = responseException.getStatusCode();
            return status.value() == 429 || status.is5xxServerError();
        }
        return false;
    }

    private void sleep(long milliseconds) {
        if (milliseconds <= 0) return;
        try {
            sleeper.sleep(milliseconds);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new KisDailyPriceRequestInterruptedException(exception);
        }
    }
}
