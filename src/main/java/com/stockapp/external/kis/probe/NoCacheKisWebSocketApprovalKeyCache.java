package com.stockapp.external.kis.probe;

import com.stockapp.external.kis.KisWebSocketApprovalKeyCache;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Profile("kis-websocket-probe")
public class NoCacheKisWebSocketApprovalKeyCache
        implements KisWebSocketApprovalKeyCache {

    @Override
    public String get(String key) {
        return null;
    }

    @Override
    public void put(String key, String approvalKey, Duration ttl) {
        // The one-shot probe intentionally does not persist credentials.
    }
}
