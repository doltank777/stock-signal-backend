package com.stockapp.external.kis;

import com.stockapp.global.config.KisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class KisTradingCalendarClientTest {

    private MockRestServiceServer server;
    private KisTradingCalendarClient client;
    private KisTradingCalendarSleeper sleeper;
    private KisTradingCalendarAccessTokenProvider tokenProvider;
    private KisProperties properties;

    @BeforeEach
    void setUp() {
        properties = new KisProperties();
        properties.setBaseUrl("https://example.com");
        properties.setAppKey("app-key");
        properties.setAppSecret("app-secret");
        properties.getTradingCalendar().setBaseUrl("https://calendar.example.com");
        properties.getTradingCalendar().setAppKey("calendar-app-key");
        properties.getTradingCalendar().setAppSecret("calendar-app-secret");
        tokenProvider = mock(KisTradingCalendarAccessTokenProvider.class);
        when(tokenProvider.getAccessToken()).thenReturn("calendar-token");
        sleeper = mock(KisTradingCalendarSleeper.class);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KisTradingCalendarClient(
                properties, tokenProvider, builder, sleeper);
    }

    @Test
    void parsesOpenAndClosedDatesAndUsesOfficialContract() {
        server.expect(queryParam("BASS_DT", "20260814"))
                .andExpect(request -> assertThat(request.getURI().toString())
                        .startsWith("https://calendar.example.com/"))
                .andExpect(queryParam("CTX_AREA_FK", ""))
                .andExpect(queryParam("CTX_AREA_NK", ""))
                .andExpect(header(HttpHeaders.AUTHORIZATION,
                        "Bearer calendar-token"))
                .andExpect(header("appkey", "calendar-app-key"))
                .andExpect(header("appsecret", "calendar-app-secret"))
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
    void missingCalendarConfigurationFailsBeforeTokenOrHttpCall() {
        KisProperties properties = new KisProperties();
        properties.setBaseUrl("https://general-paper.example.com");
        properties.setAppKey("general-key");
        properties.setAppSecret("general-secret");
        KisTradingCalendarAccessTokenProvider tokenProvider =
                mock(KisTradingCalendarAccessTokenProvider.class);
        KisTradingCalendarClient unconfiguredClient =
                new KisTradingCalendarClient(
                        properties, tokenProvider, RestClient.builder(),
                        mock(KisTradingCalendarSleeper.class));

        assertThatThrownBy(() -> unconfiguredClient.getTradingDays(
                LocalDate.of(2026, 8, 14)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Trading Calendar real environment")
                .hasMessageNotContaining("general-key")
                .hasMessageNotContaining("general-secret");
        verifyNoInteractions(tokenProvider);
    }

    @Test
    void followsContinuationKeysAndRejectsInvalidFlag() throws Exception {
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
        verify(sleeper).sleep(1000);
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
    void exposesReadOnlyPaginationDiagnosticsAndActualOutputFieldNames()
            throws Exception {
        server.expect(queryParam("CTX_AREA_FK", ""))
                .andRespond(withSuccess(responseWithAdditionalField("FK1", "NK1"),
                                MediaType.APPLICATION_JSON)
                        .header("tr_cont", "M"));
        server.expect(queryParam("CTX_AREA_FK", "FK1"))
                .andExpect(queryParam("CTX_AREA_NK", "NK1"))
                .andRespond(withSuccess(response("", "", ""),
                        MediaType.APPLICATION_JSON));

        KisTradingCalendarFetchResult result =
                client.getTradingDaysWithDiagnostics(
                        LocalDate.of(2026, 8, 14));

        assertThat(result.days()).hasSize(4);
        assertThat(result.pages()).hasSize(2);
        assertThat(result.apiCallCount()).isEqualTo(2);
        assertThat(result.pages().getFirst().attemptCount()).isEqualTo(1);
        assertThat(result.pages().getFirst().trCont()).isEqualTo("M");
        assertThat(result.pages().getFirst().contextAreaFkPresent()).isTrue();
        assertThat(result.pages().getFirst().outputFieldNames())
                .contains("bass_dt", "opnd_yn", "wday_dvsn_cd");
        verify(sleeper).sleep(1000);
        server.verify();
    }

    @Test
    void singlePageDoesNotSleepAndCountsOneCall() throws Exception {
        server.expect(queryParam("BASS_DT", "20260814"))
                .andRespond(withSuccess(response("", "", ""),
                        MediaType.APPLICATION_JSON));

        KisTradingCalendarFetchResult result =
                client.getTradingDaysWithDiagnostics(
                        LocalDate.of(2026, 8, 14));

        assertThat(result.pages()).hasSize(1);
        assertThat(result.apiCallCount()).isEqualTo(1);
        verifyNoInteractions(sleeper);
    }

    @Test
    void retriesHttpRateLimitOnceAndReusesToken() throws Exception {
        server.expect(queryParam("BASS_DT", "20260814"))
                .andRespond(rateLimitResponse());
        server.expect(queryParam("BASS_DT", "20260814"))
                .andRespond(withSuccess(response("", "", ""),
                        MediaType.APPLICATION_JSON));

        KisTradingCalendarFetchResult result =
                client.getTradingDaysWithDiagnostics(
                        LocalDate.of(2026, 8, 14));

        assertThat(result.apiCallCount()).isEqualTo(2);
        assertThat(result.pages()).singleElement()
                .satisfies(page -> assertThat(page.attemptCount()).isEqualTo(2));
        verify(sleeper).sleep(61000);
        verify(tokenProvider).getAccessToken();
    }

    @Test
    void repeatedHttpRateLimitPreservesKisErrorAndStops() throws Exception {
        server.expect(queryParam("BASS_DT", "20260814"))
                .andRespond(rateLimitResponse());
        server.expect(queryParam("BASS_DT", "20260814"))
                .andRespond(rateLimitResponse());

        assertThatThrownBy(() -> client.getTradingDays(
                LocalDate.of(2026, 8, 14)))
                .isInstanceOfSatisfying(KisApiException.class, exception -> {
                    assertThat(exception.getMessageCode()).isEqualTo("EGW00201");
                    assertThat(exception.getMessage())
                            .isEqualTo("초당 거래건수를 초과하였습니다.");
                });
        verify(sleeper).sleep(61000);
        verify(tokenProvider).getAccessToken();
    }

    @Test
    void otherHttpErrorAndUnsupportedEnvironmentAreNotRetried() {
        server.expect(queryParam("BASS_DT", "20260814"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(errorResponse("OTHER", "other failure")));

        assertThatThrownBy(() -> client.getTradingDays(
                LocalDate.of(2026, 8, 14)))
                .isInstanceOf(org.springframework.web.client.HttpServerErrorException.class);
        verifyNoInteractions(sleeper);

        setUp();
        server.expect(queryParam("BASS_DT", "20260814"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(errorResponse("EGW02006", "모의투자 TR 이 아닙니다.")));
        assertThatThrownBy(() -> client.getTradingDays(
                LocalDate.of(2026, 8, 14)))
                .isInstanceOf(org.springframework.web.client.HttpServerErrorException.class);
        verifyNoInteractions(sleeper);
    }

    @Test
    void continuationAndRateLimitRetryKeepPageAndCallCounts() throws Exception {
        server.expect(queryParam("CTX_AREA_FK", ""))
                .andRespond(withSuccess(response("FK1", "NK1", ""),
                                MediaType.APPLICATION_JSON)
                        .header("tr_cont", "M"));
        server.expect(queryParam("CTX_AREA_FK", "FK1"))
                .andRespond(rateLimitResponse());
        server.expect(queryParam("CTX_AREA_FK", "FK1"))
                .andRespond(withSuccess(response("", "", ""),
                        MediaType.APPLICATION_JSON));

        KisTradingCalendarFetchResult result =
                client.getTradingDaysWithDiagnostics(
                        LocalDate.of(2026, 8, 14));

        assertThat(result.pages()).hasSize(2);
        assertThat(result.apiCallCount()).isEqualTo(3);
        assertThat(result.pages().get(1).attemptCount()).isEqualTo(2);
        verify(sleeper).sleep(1000);
        verify(sleeper).sleep(61000);
        verify(tokenProvider).getAccessToken();
    }

    @Test
    void collectsMoreThanTenPagesUntilContinuationEnds() throws Exception {
        expectSuccessfulPages(12, false);

        KisTradingCalendarFetchResult result =
                client.getTradingDaysWithDiagnostics(
                        LocalDate.of(2026, 8, 14));

        assertThat(result.pages()).hasSize(12);
        assertThat(result.days()).hasSize(24);
        assertThat(result.apiCallCount()).isEqualTo(12);
        verify(sleeper, times(11)).sleep(1000);
        verify(tokenProvider).getAccessToken();
        server.verify();
    }

    @Test
    void failsAfterCompletingConfiguredSafetyLimitWithoutNextRequest()
            throws Exception {
        properties.getTradingCalendar().setMaxPages(3);
        expectDatedPage("", "FK1", "NK1",
                "20260820", "20260821", "20260822");
        expectDatedPage("FK1", "FK2", "NK2",
                "20260823", "20260824", "20260825");
        expectDatedPage("FK2", "FK3", "NK3",
                "20260826", "20260827", "20260828");

        assertThatThrownBy(() -> client.getTradingDaysWithDiagnostics(
                LocalDate.of(2026, 8, 14)))
                .isInstanceOfSatisfying(
                        KisTradingCalendarPaginationLimitException.class,
                        exception -> {
                            assertThat(exception.getMaxPages()).isEqualTo(3);
                            assertThat(exception.getCompletedPages()).isEqualTo(3);
                            assertThat(exception.getApiCallCount()).isEqualTo(3);
                            assertThat(exception.getPartialFetchResult().days())
                                    .hasSize(9)
                                    .extracting(day -> day.tradeDate())
                                    .containsExactly(
                                            LocalDate.of(2026, 8, 20),
                                            LocalDate.of(2026, 8, 21),
                                            LocalDate.of(2026, 8, 22),
                                            LocalDate.of(2026, 8, 23),
                                            LocalDate.of(2026, 8, 24),
                                            LocalDate.of(2026, 8, 25),
                                            LocalDate.of(2026, 8, 26),
                                            LocalDate.of(2026, 8, 27),
                                            LocalDate.of(2026, 8, 28));
                            assertThat(exception.getPartialFetchResult().pages())
                                    .hasSize(3)
                                    .allSatisfy(page -> assertThat(page.responseOrder())
                                            .isEqualTo(KisTradingCalendarResponseOrder.ASCENDING));
                            assertThat(exception.getPartialFetchResult().pages().getFirst())
                                    .satisfies(page -> {
                                        assertThat(page.firstDate()).isEqualTo(
                                                LocalDate.of(2026, 8, 20));
                                        assertThat(page.lastDate()).isEqualTo(
                                                LocalDate.of(2026, 8, 22));
                                    });
                            assertThat(exception.getMessage())
                                    .contains("maxPages=3")
                                    .contains("completedPages=3")
                                    .contains("apiCallCount=3")
                                    .contains("collectedRowCount=9");
                        });
        verify(sleeper, times(2)).sleep(1000);
        verify(tokenProvider).getAccessToken();
        server.verify();
    }

    @Test
    void succeedsWhenContinuationEndsExactlyAtSafetyLimit()
            throws Exception {
        properties.getTradingCalendar().setMaxPages(3);
        expectSuccessfulPages(3, false);

        KisTradingCalendarFetchResult result =
                client.getTradingDaysWithDiagnostics(
                        LocalDate.of(2026, 8, 14));

        assertThat(result.pages()).hasSize(3);
        assertThat(result.apiCallCount()).isEqualTo(3);
        verify(sleeper, times(2)).sleep(1000);
        server.verify();
    }

    @Test
    void repeatedContinuationStateFailsBeforeAnotherRequest()
            throws Exception {
        server.expect(queryParam("CTX_AREA_FK", ""))
                .andRespond(withSuccess(response("FK1", "NK1", ""),
                                MediaType.APPLICATION_JSON)
                        .header("tr_cont", "M"));
        server.expect(queryParam("CTX_AREA_FK", "FK1"))
                .andRespond(withSuccess(response("FK1", "NK1", ""),
                                MediaType.APPLICATION_JSON)
                        .header("tr_cont", "M"));

        assertThatThrownBy(() -> client.getTradingDaysWithDiagnostics(
                LocalDate.of(2026, 8, 14)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pagination did not progress");
        verify(sleeper).sleep(1000);
        server.verify();
    }

    @Test
    void interruptionRestoresFlagAndStopsBeforeNextPage() throws Exception {
        server.expect(queryParam("CTX_AREA_FK", ""))
                .andRespond(withSuccess(response("FK1", "NK1", ""),
                                MediaType.APPLICATION_JSON)
                        .header("tr_cont", "M"));
        doThrow(new InterruptedException()).when(sleeper).sleep(1000);

        try {
            assertThatThrownBy(() -> client.getTradingDays(
                    LocalDate.of(2026, 8, 14)))
                    .isInstanceOf(KisTradingCalendarInterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(tokenProvider).getAccessToken();
        } finally {
            Thread.interrupted();
        }
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

    @Test
    void rangeStopsOnFirstCoveringPageAndFiltersRowsAfterEndDate() {
        expectDatedPage("", "FK1", "NK1",
                "20260820", "20260821", "20260822", "20260823");

        KisTradingCalendarFetchResult result =
                client.getTradingDaysWithDiagnostics(
                        LocalDate.of(2026, 8, 20),
                        LocalDate.of(2026, 8, 22));

        assertThat(result.days()).extracting(day -> day.tradeDate())
                .containsExactly(LocalDate.of(2026, 8, 20),
                        LocalDate.of(2026, 8, 21),
                        LocalDate.of(2026, 8, 22));
        assertThat(result.requestedRangeComplete()).isTrue();
        assertThat(result.sourcePaginationComplete()).isFalse();
        assertThat(result.sourceHasMore()).isTrue();
        assertThat(result.pages()).hasSize(1);
        assertThat(result.apiCallCount()).isEqualTo(1);
        verifyNoInteractions(sleeper);
        server.verify();
    }

    @Test
    void rangeStopsOnCoveringPageWithoutRequestingAnotherPage()
            throws Exception {
        properties.getTradingCalendar().setMaxPages(3);
        expectDatedPage("", "FK1", "NK1", "20260820", "20260821");
        expectDatedPage("FK1", "FK2", "NK2", "20260822", "20260823");
        expectDatedPage("FK2", "FK3", "NK3", "20260824", "20260825");

        KisTradingCalendarFetchResult result =
                client.getTradingDaysWithDiagnostics(
                        LocalDate.of(2026, 8, 20),
                        LocalDate.of(2026, 8, 25));

        assertThat(result.days()).hasSize(6);
        assertThat(result.pages()).hasSize(3);
        assertThat(result.requestedRangeComplete()).isTrue();
        verify(sleeper, times(2)).sleep(1000);
        server.verify();
    }

    @Test
    void rangeFailsClosedForSourceEndGapAndDuplicate() {
        server.expect(queryParam("CTX_AREA_FK", ""))
                .andRespond(withSuccess(datedResponse("", "",
                                "20260820", "20260821"),
                        MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.getTradingDays(
                LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 22)))
                .isInstanceOf(KisTradingCalendarRangeIncompleteException.class)
                .hasMessageContaining("ended before requested end date");
        server.verify();

        setUp();
        expectDatedPage("", "FK1", "NK1",
                "20260820", "20260822", "20260823");
        assertThatThrownBy(() -> client.getTradingDays(
                LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 22)))
                .isInstanceOf(KisTradingCalendarRangeIncompleteException.class)
                .hasMessageContaining("missing calendar date: 2026-08-21");
        server.verify();

        setUp();
        expectDatedPage("", "FK1", "NK1",
                "20260820", "20260821", "20260821", "20260822");
        assertThatThrownBy(() -> client.getTradingDays(
                LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 22)))
                .isInstanceOf(KisTradingCalendarRangeIncompleteException.class)
                .hasMessageContaining("duplicate date: 2026-08-21");
        server.verify();
    }

    @Test
    void validatesRangeBeforeTokenOrHttpCall() {
        assertThatThrownBy(() -> client.getTradingDays(
                LocalDate.of(2026, 8, 21), LocalDate.of(2026, 8, 20)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endDate must be on or after startDate");
        verifyNoInteractions(tokenProvider);
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

    private String responseWithAdditionalField(String fk, String nk) {
        return """
                {"rt_cd":"0","msg_cd":"OK","msg1":"success",
                 "ctx_area_fk":"%s","ctx_area_nk":"%s","output":[
                  {"bass_dt":"20260814","opnd_yn":"Y","wday_dvsn_cd":"5"},
                  {"bass_dt":"20260815","opnd_yn":"N"}]}
                """.formatted(fk, nk);
    }

    private void expectDatedPage(
            String requestFk,
            String responseFk,
            String responseNk,
            String... dates
    ) {
        server.expect(queryParam("CTX_AREA_FK", requestFk))
                .andRespond(withSuccess(datedResponse(responseFk, responseNk, dates),
                                MediaType.APPLICATION_JSON)
                        .header("tr_cont", "M"));
    }

    private String datedResponse(String fk, String nk, String... dates) {
        String rows = java.util.Arrays.stream(dates)
                .map(date -> "{\"bass_dt\":\"" + date + "\",\"opnd_yn\":\"Y\"}")
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"rt_cd\":\"0\",\"msg_cd\":\"OK\",\"msg1\":\"success\","
                + "\"ctx_area_fk\":\"" + fk + "\",\"ctx_area_nk\":\"" + nk
                + "\",\"output\":[" + rows + "]}";
    }

    private org.springframework.test.web.client.ResponseCreator rateLimitResponse() {
        return withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorResponse(
                        "EGW00201", "초당 거래건수를 초과하였습니다."));
    }

    private void expectSuccessfulPages(
            int pageCount,
            boolean continueAfterLastPage
    ) {
        for (int page = 1; page <= pageCount; page++) {
            String requestFk = page == 1 ? "" : "FK" + (page - 1);
            boolean hasNext = page < pageCount || continueAfterLastPage;
            String responseFk = hasNext ? "FK" + page : "";
            String responseNk = hasNext ? "NK" + page : "";
            var responseCreator = withSuccess(
                    response(responseFk, responseNk, ""),
                    MediaType.APPLICATION_JSON);
            if (hasNext) {
                responseCreator.header("tr_cont", "M");
            }
            server.expect(queryParam("CTX_AREA_FK", requestFk))
                    .andRespond(responseCreator);
        }
    }

    private String errorResponse(String code, String message) {
        return """
                {"rt_cd":"1","msg_cd":"%s","msg1":"%s"}
                """.formatted(code, message);
    }
}
