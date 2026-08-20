package com.stockapp.external.kis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Getter
public class KisTradingCalendarResponse {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.BASIC_ISO_DATE;

    @JsonProperty("rt_cd") private String rtCd;
    @JsonProperty("msg_cd") private String msgCd;
    @JsonProperty("msg1") private String message;
    @JsonProperty("ctx_area_fk") private String contextAreaFk;
    @JsonProperty("ctx_area_nk") private String contextAreaNk;
    private List<Output> output;

    public List<KisTradingDay> toTradingDays() {
        if (output == null) {
            throw new IllegalArgumentException(
                    "KIS trading calendar output is missing");
        }
        return output.stream().map(Output::toTradingDay).toList();
    }

    @Getter
    public static class Output {
        @JsonProperty("bass_dt") private String baseDate;
        @JsonProperty("opnd_yn") private String openYn;

        KisTradingDay toTradingDay() {
            if (baseDate == null || baseDate.isBlank()) {
                throw new IllegalArgumentException(
                        "KIS trading calendar bass_dt is required");
            }
            LocalDate date;
            try {
                date = LocalDate.parse(baseDate, FORMATTER);
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException(
                        "invalid KIS trading calendar bass_dt: " + baseDate,
                        exception);
            }
            if (!"Y".equals(openYn) && !"N".equals(openYn)) {
                throw new IllegalArgumentException(
                        "invalid KIS trading calendar opnd_yn for " + date);
            }
            return new KisTradingDay(date, "Y".equals(openYn));
        }
    }
}
