package com.stockapp.external.kis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class KisTokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("access_token_token_expired")
    private String accessTokenExpired;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("expires_in")
    private Long expiresIn;
}