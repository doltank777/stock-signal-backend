package com.stockapp.domain.notification;

import com.stockapp.domain.notification.dto.NotificationTokenRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notification-tokens")
@RequiredArgsConstructor
public class NotificationTokenController {

    private final NotificationTokenService notificationTokenService;

    @PostMapping
    public ResponseEntity<Void> saveToken(
            Authentication authentication,
            @RequestBody NotificationTokenRequest request
    ) {
        String email = authentication.getName();

        notificationTokenService.saveToken(email, request);

        return ResponseEntity.ok().build();
    }
}