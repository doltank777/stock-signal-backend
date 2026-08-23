package com.stockapp.external.kis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Getter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

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

    public Set<String> outputFieldNames() {
        Set<String> names = new LinkedHashSet<>();
        if (output != null) {
            output.forEach(row -> names.addAll(row.fieldNames));
        }
        return Set.copyOf(names);
    }

    @Getter
    public static class Output {
        private String baseDate;
        private String openYn;
        private final Set<String> fieldNames = new LinkedHashSet<>();

        @JsonSetter("bass_dt")
        void setBaseDate(String baseDate) {
            this.baseDate = baseDate;
            fieldNames.add("bass_dt");
        }

        @JsonSetter("opnd_yn")
        void setOpenYn(String openYn) {
            this.openYn = openYn;
            fieldNames.add("opnd_yn");
        }

        @JsonAnySetter
        void captureAdditionalField(String name, Object ignoredValue) {
            fieldNames.add(name);
        }

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
