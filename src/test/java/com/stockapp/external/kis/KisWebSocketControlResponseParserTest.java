package com.stockapp.external.kis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KisWebSocketControlResponseParserTest {

    private final KisWebSocketControlResponseParser parser =
            new KisWebSocketControlResponseParser(new ObjectMapper());

    @Test
    void parsesSuccessAndErrorResponses() {
        String success = """
                {"header":{"tr_id":"H0STCNT0","tr_key":"005930"},
                 "body":{"rt_cd":"0","msg_cd":"OPSP0000","msg1":"SUBSCRIBE SUCCESS"}}
                """;
        String failure = """
                {"header":{"tr_id":"H0STCNT0","tr_key":"000660"},
                 "body":{"rt_cd":"9","msg_cd":"OPSP8996","msg1":"ALREADY IN USE appkey"}}
                """;

        assertThat(parser.parse(success)).hasValueSatisfying(response -> {
            assertThat(response.trId()).isEqualTo("H0STCNT0");
            assertThat(response.trKey()).isEqualTo("005930");
            assertThat(response.isSuccess()).isTrue();
        });
        assertThat(parser.parse(failure)).hasValueSatisfying(response -> {
            assertThat(response.isSuccess()).isFalse();
            assertThat(response.messageCode()).isEqualTo("OPSP8996");
            assertThat(response.message()).isEqualTo("ALREADY IN USE appkey");
        });
    }

    @Test
    void safelyRejectsMalformedOrIncompleteJson() {
        assertThat(parser.parse("not-json")).isEmpty();
        assertThat(parser.parse("{\"header\":{}}")).isEmpty();
    }
}
