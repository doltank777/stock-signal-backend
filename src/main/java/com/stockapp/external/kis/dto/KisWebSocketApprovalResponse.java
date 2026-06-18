package com.stockapp.external.kis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class KisWebSocketApprovalResponse {

    @JsonProperty("approval_key")
    private String approvalKey;
}