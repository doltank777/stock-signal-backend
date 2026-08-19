package com.stockapp.external.kis;

public record KisWebSocketControlResponse(
        String trId,
        String trKey,
        String returnCode,
        String messageCode,
        String message
) {
    public boolean isSuccess() {
        return "0".equals(returnCode);
    }
}
