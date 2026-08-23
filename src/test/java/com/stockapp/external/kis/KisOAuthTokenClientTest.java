package com.stockapp.external.kis;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KisOAuthTokenClientTest {

    @Test
    void requestsTokenFromExplicitEnvironmentWithoutLoggingCredentials() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder)
                .build();
        server.expect(requestTo(
                        "https://calendar.example.com/oauth2/tokenP"))
                .andExpect(content().json("""
                        {"grant_type":"client_credentials",
                         "appkey":"calendar-key",
                         "appsecret":"calendar-secret"}
                        """))
                .andRespond(withSuccess(
                        "{\"access_token\":\"calendar-token\"}",
                        MediaType.APPLICATION_JSON));

        String token = new KisOAuthTokenClient(builder).requestAccessToken(
                "https://calendar.example.com",
                "calendar-key",
                "calendar-secret");

        assertThat(token).isEqualTo("calendar-token");
        server.verify();
    }
}
