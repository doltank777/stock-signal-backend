package com.stockapp.external.kis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class KisStockPriceResponse {

    @JsonProperty("rt_cd")
    private String rtCd;

    @JsonProperty("msg_cd")
    private String msgCd;

    @JsonProperty("msg1")
    private String msg;

    private Output output;

    @Getter
    public static class Output {

        // 현재가
        @JsonProperty("stck_prpr")
        private String currentPrice;

        // 전일 대비
        @JsonProperty("prdy_vrss")
        private String priceChange;

        // 전일 대비율
        @JsonProperty("prdy_ctrt")
        private String changeRate;

        // 누적 거래량
        @JsonProperty("acml_vol")
        private String volume;

        // 누적 거래대금
        @JsonProperty("acml_tr_pbmn")
        private String tradeAmount;

        // 종목명
        @JsonProperty("hts_kor_isnm")
        private String stockName;
    }
}