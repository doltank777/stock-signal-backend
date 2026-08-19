package com.stockapp.external.kis;

public enum KisWebSocketOperation {
    SUBSCRIBE("1"),
    UNSUBSCRIBE("2");

    private final String transactionType;

    KisWebSocketOperation(String transactionType) {
        this.transactionType = transactionType;
    }

    public String transactionType() {
        return transactionType;
    }
}
