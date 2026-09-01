package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.MarketType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KisMasterDownloaderTest {

    @Test
    void downloadsZipBytesWithoutAuthentication() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        byte[] archive = KisMasterFixture.zip("kospi_code.mst", "content".getBytes());
        server.expect(once(), requestTo(
                        "https://new.real.download.dws.co.kr/common/master/kospi_code.mst.zip"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(archive, null));

        assertThat(new KisMasterDownloader(builder).download(MarketType.KOSPI))
                .isEqualTo(archive);
        server.verify();
    }

    @Test
    void rejectsNonSuccessfulStatus() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo(
                        "https://new.real.download.dws.co.kr/common/master/kosdaq_code.mst.zip"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> new KisMasterDownloader(builder).download(MarketType.KOSDAQ))
                .isInstanceOf(KisMasterException.class)
                .hasMessageContaining("Failed to download KIS Master");
        server.verify();
    }

    @Test
    void rejectsEmptyBody() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo(
                        "https://new.real.download.dws.co.kr/common/master/kospi_code.mst.zip"))
                .andRespond(withSuccess(new byte[0], null));

        assertThatThrownBy(() -> new KisMasterDownloader(builder).download(MarketType.KOSPI))
                .isInstanceOf(KisMasterException.class)
                .hasMessageContaining("response body is empty");
        server.verify();
    }
}
