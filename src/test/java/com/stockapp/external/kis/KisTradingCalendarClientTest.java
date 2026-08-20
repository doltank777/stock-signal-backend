package com.stockapp.external.kis;

import com.stockapp.global.config.KisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KisTradingCalendarClientTest {

    private MockRestServiceServer server;
    private KisTradingCalendarClient client;

    @BeforeEach
    void setUp() {
        KisProperties properties = new KisProperties();
        properties.setBaseUrl("https://example.com");
        properties.setAppKey("app-key");
        properties.setAppSecret("app-secret");
        KisAccessTokenProvider tokenProvider =
                mock(KisAccessTokenProvider.class);
        when(tokenProvider.getAccessToken()).thenReturn("token");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KisTradingCalendarClient(
                properties, tokenProvider, builder);
    }

    @Test
    void parsesOpenAndClosedDatesAndUsesOfficialContract() {
        server.expect(queryParam("BASS_DT", "20260814"))
                .andExpect(queryParam("CTX_AREA_FK", ""))
                .andExpect(queryParam("CTX_AREA_NK", ""))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andExpect(header("tr_id", "CTCA0903R"))
                .andRespond(withSuccess(response("", "", ""),
                        MediaType.APPLICATION_JSON));

        var days = client.getTradingDays(LocalDate.of(2026, 8, 14));

        assertThat(days).extracting(day -> day.tradeDate())
                .containsExactly(LocalDate.of(2026, 8, 14),
                        LocalDate.of(2026, 8, 15));
        assertThat(days).extracting(day -> day.tradingDay())
                .containsExactly(true, false);
        server.verify();
    }

    @Test
    void followsContinuationKeysAndRejectsInvalidFlag() {
        server.expect(queryParam("CTX_AREA_FK", ""))
                .andRespond(withSuccess(response("FK1", "NK1", "Y"),
                                MediaType.APPLICATION_JSON)
                        .header("tr_cont", "M"));
        server.expect(queryParam("CTX_AREA_FK", "FK1"))
                .andExpect(queryParam("CTX_AREA_NK", "NK1"))
                .andExpect(header("tr_cont", "N"))
                .andRespond(withSuccess(response("", "", ""),
                        MediaType.APPLICATION_JSON));
        assertThat(client.getTradingDays(LocalDate.of(2026, 8, 14)))
                .hasSize(4);
        server.verify();

        setUp();
        server.expect(queryParam("BASS_DT", "20260814"))
                .andRespond(withSuccess(response("", "", "X"),
                        MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.getTradingDays(
                LocalDate.of(2026, 8, 14)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("opnd_yn");
    }

    @Test
    void preservesKisBusinessError() {
        server.expect(queryParam("BASS_DT", "20260814"))
                .andRespond(withSuccess(
                        "{\"rt_cd\":\"1\",\"msg_cd\":\"ERROR\",\"msg1\":\"failed\"}",
                        MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.getTradingDays(
                LocalDate.of(2026, 8, 14)))
                .isInstanceOfSatisfying(KisApiException.class,
                        exception -> assertThat(exception.getMessageCode())
                                .isEqualTo("ERROR"));
    }

    private String response(String fk, String nk, String overrideFlag) {
        String secondFlag = overrideFlag.isEmpty() ? "N" : overrideFlag;
        return """
                {"rt_cd":"0","msg_cd":"OK","msg1":"success",
                 "ctx_area_fk":"%s","ctx_area_nk":"%s","output":[
                  {"bass_dt":"20260814","opnd_yn":"Y"},
                  {"bass_dt":"20260815","opnd_yn":"%s"}]}
                """.formatted(fk, nk, secondFlag);
    }
}
