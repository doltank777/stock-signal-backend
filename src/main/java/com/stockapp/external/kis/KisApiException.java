package com.stockapp.external.kis;

import lombok.Getter;

@Getter
public class KisApiException extends RuntimeException {

    private final String messageCode;

    public KisApiException(String messageCode, String message) {
        super(message);
        this.messageCode = messageCode;
    }
}
