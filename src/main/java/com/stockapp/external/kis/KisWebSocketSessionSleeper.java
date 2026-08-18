package com.stockapp.external.kis;

import org.springframework.stereotype.Component;

@Component
public class KisWebSocketSessionSleeper {

    public void sleep(long milliseconds) throws InterruptedException {
        Thread.sleep(milliseconds);
    }
}
