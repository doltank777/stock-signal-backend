package com.stockapp.domain.stock;

import org.springframework.stereotype.Component;

@Component
public class DailyPriceLoadSleeper {

    public void sleep(long milliseconds) throws InterruptedException {
        Thread.sleep(milliseconds);
    }
}
