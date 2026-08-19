package com.stockapp.external.kis;

import java.time.Duration;

public interface KisWebSocketApprovalKeyCache {

    String get(String key);

    void put(String key, String approvalKey, Duration ttl);
}
