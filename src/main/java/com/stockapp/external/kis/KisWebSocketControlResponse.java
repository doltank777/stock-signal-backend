package com.stockapp.external.kis;

public record KisWebSocketControlResponse(
        String trId,
        String trKey,
        String returnCode,
        String messageCode,
        String message
) {
    public boolean isAckLike() {
        return returnCode != null && !returnCode.isBlank()
                && ((messageCode != null && !messageCode.isBlank())
                || (message != null && !message.isBlank()));
    }

    public boolean isSuccess() {
        return "0".equals(returnCode);
    }
}
