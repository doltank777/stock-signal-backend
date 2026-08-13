package com.stockapp.external.kis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;

@Getter
public class KisDailyPriceResponse {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.BASIC_ISO_DATE;

    @JsonProperty("rt_cd")
    private String rtCd;

    @JsonProperty("msg_cd")
    private String msgCd;

    @JsonProperty("msg1")
    private String msg;

    private JsonNode output1;

    private List<Output2> output2;

    public List<KisDailyPrice> toDailyPrices() {
        if (output2 == null) {
            return Collections.emptyList();
        }

        return output2.stream()
                .map(Output2::toDailyPrice)
                .toList();
    }

    @Getter
    public static class Output2 {

        @JsonProperty("stck_bsop_date")
        private String tradeDate;

        @JsonProperty("stck_oprc")
        private String openPrice;

        @JsonProperty("stck_hgpr")
        private String highPrice;

        @JsonProperty("stck_lwpr")
        private String lowPrice;

        @JsonProperty("stck_clpr")
        private String closePrice;

        @JsonProperty("acml_vol")
        private String volume;

        public KisDailyPrice toDailyPrice() {
            return KisDailyPrice.builder()
                    .tradeDate(parseDate(tradeDate))
                    .openPrice(parseLong("stck_oprc", openPrice))
                    .highPrice(parseLong("stck_hgpr", highPrice))
                    .lowPrice(parseLong("stck_lwpr", lowPrice))
                    .closePrice(parseLong("stck_clpr", closePrice))
                    .volume(parseLong("acml_vol", volume))
                    .build();
        }

        private LocalDate parseDate(String value) {
            requireValue("stck_bsop_date", value);

            try {
                return LocalDate.parse(value, DATE_FORMATTER);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException(
                        "KIS 일봉 거래일 형식이 올바르지 않습니다: " + value,
                        e);
            }
        }

        private Long parseLong(String fieldName, String value) {
            requireValue(fieldName, value);

            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "KIS 일봉 숫자 형식이 올바르지 않습니다: "
                                + fieldName + "=" + value,
                        e);
            }
        }

        private void requireValue(String fieldName, String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(
                        "KIS 일봉 필드가 비어 있습니다: " + fieldName);
            }
        }
    }
}
