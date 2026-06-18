package com.stockapp.external.kis;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/kis/websocket")
public class KisWebSocketTestController {

    private final KisWebSocketClient kisWebSocketClient;
    private final KisWebSocketApprovalClient kisWebSocketApprovalClient;

    @GetMapping("/approval-key")
    public String getApprovalKey() {
        return kisWebSocketApprovalClient.getApprovalKey();
    }

    @PostMapping("/subscribe/{stockCode}")
    public String subscribe(@PathVariable String stockCode) {
        kisWebSocketClient.connectAndSubscribe(stockCode);
        return "KIS WebSocket 구독 요청 완료: " + stockCode;
    }
}