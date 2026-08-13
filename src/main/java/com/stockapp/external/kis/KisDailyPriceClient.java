package com.stockapp.external.kis;

import com.stockapp.external.kis.dto.KisDailyPrice;
import com.stockapp.external.kis.dto.KisDailyPriceResponse;
import com.stockapp.global.config.KisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
public class KisDailyPriceClient {

    private static final String API_PATH =
            "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice";
    private static final String TR_ID = "FHKST03010100";
    private static final String MARKET_DIV_CODE = "J";
    private static final String PERIOD_DIV_CODE = "D";
    private static final String ADJUSTED_PRICE_CODE = "0";
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.BASIC_ISO_DATE;

    private final KisProperties kisProperties;
    private final KisAuthClient kisAuthClient;
    private final RestClient.Builder restClientBuilder;

    public List<KisDailyPrice> getDailyPrices(
            String stockCode,
            LocalDate startDate,
            LocalDate endDate) {

        validateRequest(stockCode, startDate, endDate);

        String accessToken = kisAuthClient.getAccessToken();

        RestClient restClient = restClientBuilder
                .baseUrl(kisProperties.getBaseUrl())
                .build();

        KisDailyPriceResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(API_PATH)
                        .queryParam("FID_COND_MRKT_DIV_CODE", MARKET_DIV_CODE)
                        .queryParam("FID_INPUT_ISCD", stockCode)
                        .queryParam(
                                "FID_INPUT_DATE_1",
                                startDate.format(DATE_FORMATTER))
                        .queryParam(
                                "FID_INPUT_DATE_2",
                                endDate.format(DATE_FORMATTER))
                        .queryParam("FID_PERIOD_DIV_CODE", PERIOD_DIV_CODE)
                        .queryParam("FID_ORG_ADJ_PRC", ADJUSTED_PRICE_CODE)
                        .build())
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", kisProperties.getAppKey())
                .header("appsecret", kisProperties.getAppSecret())
                .header("tr_id", TR_ID)
                .retrieve()
                .body(KisDailyPriceResponse.class);

        if (response == null) {
            throw new IllegalArgumentException("KIS 일봉 조회 응답이 없습니다.");
        }

        if (!"0".equals(response.getRtCd())) {
            throw new IllegalArgumentException(
                    "KIS 일봉 조회 실패: " + response.getMsg());
        }

        return response.toDailyPrices();
    }

    private void validateRequest(
            String stockCode,
            LocalDate startDate,
            LocalDate endDate) {

        if (stockCode == null || stockCode.isBlank()) {
            throw new IllegalArgumentException("종목코드는 필수입니다.");
        }

        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("조회 시작일과 종료일은 필수입니다.");
        }

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException(
                    "조회 시작일은 종료일보다 늦을 수 없습니다.");
        }
    }
}
