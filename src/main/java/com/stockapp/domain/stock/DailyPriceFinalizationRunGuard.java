package com.stockapp.domain.stock;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class DailyPriceFinalizationRunGuard {

    private final AtomicBoolean running = new AtomicBoolean();

    public void acquire() {
        if (!running.compareAndSet(false, true)) {
            throw new DailyPriceFinalizationAlreadyRunningException();
        }
    }

    public void release() {
        running.set(false);
    }
}
