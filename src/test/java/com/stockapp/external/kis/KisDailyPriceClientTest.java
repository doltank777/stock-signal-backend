package com.stockapp.external.kis;

import com.stockapp.external.kis.dto.KisDailyPrice;
import com.stockapp.global.config.KisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KisDailyPriceClientTest {

    private MockRestServiceServer server;
    private KisDailyPriceClient client;

    @BeforeEach
    void setUp() {
        KisProperties properties = new KisProperties();
        properties.setBaseUrl("https://example.com");
        properties.setAppKey("app-key");
        properties.setAppSecret("app-secret");

        KisAuthClient authClient = mock(KisAuthClient.class);
        when(authClient.getAccessToken()).thenReturn("access-token");

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KisDailyPriceClient(
                properties,
                authClient,
                builder);
    }

    @Test
    void requestsDailyAdjustedPricesAndParsesOhlcv() {
        server.expect(requestTo(
                        "https://example.com/uapi/domestic-stock/v1/quotations/"
                                + "inquire-daily-itemchartprice"
                                + "?FID_COND_MRKT_DIV_CODE=J"
                                + "&FID_INPUT_ISCD=005930"
                                + "&FID_INPUT_DATE_1=20260801"
                                + "&FID_INPUT_DATE_2=20260813"
                                + "&FID_PERIOD_DIV_CODE=D"
                                + "&FID_ORG_ADJ_PRC=0"))
                .andExpect(header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer access-token"))
                .andExpect(header("appkey", "app-key"))
                .andExpect(header("appsecret", "app-secret"))
                .andExpect(header("tr_id", "FHKST03010100"))
                .andRespond(withSuccess("""
                        {
                          "rt_cd": "0",
                          "msg_cd": "MCA00000",
                          "msg1": "정상처리 되었습니다.",
                          "output1": {},
                          "output2": [
                            {
                              "stck_bsop_date": "20260812",
                              "stck_oprc": "70000",
                              "stck_hgpr": "72000",
                              "stck_lwpr": "69000",
                              "stck_clpr": "71000",
                              "acml_vol": "10000000"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<KisDailyPrice> prices = client.getDailyPrices(
                "005930",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 13));

        assertThat(prices).hasSize(1);
        KisDailyPrice price = prices.getFirst();
        assertThat(price.getTradeDate())
                .isEqualTo(LocalDate.of(2026, 8, 12));
        assertThat(price.getOpenPrice()).isEqualTo(70_000L);
        assertThat(price.getHighPrice()).isEqualTo(72_000L);
        assertThat(price.getLowPrice()).isEqualTo(69_000L);
        assertThat(price.getClosePrice()).isEqualTo(71_000L);
        assertThat(price.getVolume()).isEqualTo(10_000_000L);
        server.verify();
    }

    @Test
    void returnsEmptyListWhenOutput2IsEmpty() {
        expectResponse("""
                {
                  "rt_cd": "0",
                  "msg_cd": "MCA00000",
                  "msg1": "정상처리 되었습니다.",
                  "output1": {},
                  "output2": []
                }
                """);

        assertThat(client.getDailyPrices(
                "005930",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 13)))
                .isEmpty();
        server.verify();
    }

    @Test
    void rejectsInvalidDailyPriceData() {
        expectResponse("""
                {
                  "rt_cd": "0",
                  "msg_cd": "MCA00000",
                  "msg1": "정상처리 되었습니다.",
                  "output1": {},
                  "output2": [
                    {
                      "stck_bsop_date": "20260812",
                      "stck_oprc": "invalid",
                      "stck_hgpr": "72000",
                      "stck_lwpr": "69000",
                      "stck_clpr": "71000",
                      "acml_vol": "10000000"
                    }
                  ]
                }
                """);

        assertThatThrownBy(() -> client.getDailyPrices(
                "005930",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 13)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stck_oprc=invalid");
        server.verify();
    }

    @Test
    void rejectsInvalidTradeDate() {
        expectResponse("""
                {
                  "rt_cd": "0",
                  "msg_cd": "MCA00000",
                  "msg1": "정상처리 되었습니다.",
                  "output1": {},
                  "output2": [
                    {
                      "stck_bsop_date": "20260230",
                      "stck_oprc": "70000",
                      "stck_hgpr": "72000",
                      "stck_lwpr": "69000",
                      "stck_clpr": "71000",
                      "acml_vol": "10000000"
                    }
                  ]
                }
                """);

        assertThatThrownBy(() -> client.getDailyPrices(
                "005930",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 13)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("거래일 형식");
        server.verify();
    }

    private void expectResponse(String responseBody) {
        server.expect(requestTo(org.hamcrest.Matchers.any(String.class)))
                .andExpect(queryParam("FID_PERIOD_DIV_CODE", "D"))
                .andExpect(queryParam("FID_ORG_ADJ_PRC", "0"))
                .andRespond(withSuccess(
                        responseBody,
                        MediaType.APPLICATION_JSON));
    }
}
