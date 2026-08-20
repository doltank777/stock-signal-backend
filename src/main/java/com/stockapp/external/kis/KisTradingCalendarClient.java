package com.stockapp.external.kis;

import com.stockapp.external.kis.dto.KisTradingCalendarResponse;
import com.stockapp.external.kis.dto.KisTradingDay;
import com.stockapp.global.config.KisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class KisTradingCalendarClient {

    private static final String API_PATH =
            "/uapi/domestic-stock/v1/quotations/chk-holiday";
    private static final String TR_ID = "CTCA0903R";
    private static final int MAX_PAGES = 10;
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.BASIC_ISO_DATE;

    private final KisProperties properties;
    private final KisAccessTokenProvider accessTokenProvider;
    private final RestClient.Builder restClientBuilder;

    public List<KisTradingDay> getTradingDays(LocalDate baseDate) {
        if (baseDate == null) {
            throw new IllegalArgumentException("baseDate is required");
        }
        String token = accessTokenProvider.getAccessToken();
        RestClient client = restClientBuilder.baseUrl(properties.getBaseUrl())
                .build();
        List<KisTradingDay> result = new ArrayList<>();
        Set<String> continuationKeys = new HashSet<>();
        String fk = "";
        String nk = "";

        for (int page = 0; page < MAX_PAGES; page++) {
            String requestFk = fk;
            String requestNk = nk;
            RestClient.RequestHeadersSpec<?> request = client.get()
                    .uri(builder -> builder.path(API_PATH)
                            .queryParam("BASS_DT", baseDate.format(FORMATTER))
                            .queryParam("CTX_AREA_FK", requestFk)
                            .queryParam("CTX_AREA_NK", requestNk)
                            .build())
                    .header("authorization", "Bearer " + token)
                    .header("appkey", properties.getAppKey())
                    .header("appsecret", properties.getAppSecret())
                    .header("tr_id", TR_ID);
            if (page > 0) {
                request = request.header("tr_cont", "N");
            }
            ResponseEntity<KisTradingCalendarResponse> entity = request
                    .retrieve().toEntity(KisTradingCalendarResponse.class);
            KisTradingCalendarResponse response = entity.getBody();
            if (response == null) {
                throw new IllegalArgumentException(
                        "KIS trading calendar response is missing");
            }
            if (!"0".equals(response.getRtCd())) {
                throw new KisApiException(
                        response.getMsgCd(), response.getMessage());
            }
            result.addAll(response.toTradingDays());
            String trCont = entity.getHeaders().getFirst("tr_cont");
            if (!"M".equals(trCont) && !"F".equals(trCont)) {
                return List.copyOf(result);
            }
            fk = requireContinuation("ctx_area_fk", response.getContextAreaFk());
            nk = requireContinuation("ctx_area_nk", response.getContextAreaNk());
            if (!continuationKeys.add(fk + "\u0000" + nk)) {
                throw new IllegalArgumentException(
                        "KIS trading calendar pagination did not progress");
            }
        }
        throw new IllegalArgumentException(
                "KIS trading calendar pagination exceeded max pages");
    }

    private String requireContinuation(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "KIS trading calendar " + name + " is required");
        }
        return value;
    }
}
