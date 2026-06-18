package com.stockapp.external.kis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class KisWebSocketApprovalRequest {

    @JsonProperty("grant_type")
    private String grantType;

    private String appkey;

    private String secretkey;
}