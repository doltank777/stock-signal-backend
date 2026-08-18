package com.stockapp.external.kis;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.net.URI;

@Component
public class KisWebSocketConnector {

    public WebSocketSession connect(
            WebSocketHandler handler,
            URI uri
    ) throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();
        return client.execute(handler, null, uri).get();
    }
}
