package com.stockapp.external.kis;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/kis")
public class KisTestController {

    private final KisAuthClient kisAuthClient;

    @GetMapping("/token")
    public String getToken() {
        return kisAuthClient.getAccessToken();
    }
}