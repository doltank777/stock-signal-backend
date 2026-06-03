package com.stockapp.domain.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExpoPushService {

    private final RestClient.Builder restClientBuilder;

    public void sendPush(String expoPushToken, String title, String body) {
        RestClient restClient = restClientBuilder
                .baseUrl("https://exp.host")
                .build();

        Map<String, Object> requestBody = Map.of(
                "to", expoPushToken,
                "sound", "default",
                "title", title,
                "body", body
        );

        try {
            String response = restClient.post()
                    .uri("/--/api/v2/push/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            log.info("Expo Push 발송 성공: {}", response);
        } catch (Exception e) {
            log.error("Expo Push 발송 실패", e);
        }
    }
}