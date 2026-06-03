package com.stockapp.domain.notification;

import com.stockapp.domain.notification.dto.NotificationTokenRequest;
import com.stockapp.domain.user.User;
import com.stockapp.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationTokenService {

    private final NotificationTokenRepository notificationTokenRepository;
    private final UserRepository userRepository;

    @Transactional
    public void saveToken(String email, NotificationTokenRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        NotificationToken notificationToken = notificationTokenRepository.findByToken(request.getToken())
                .map(existingToken -> {
                    existingToken.update(user, request.getPlatform());
                    return existingToken;
                })
                .orElseGet(() -> new NotificationToken(
                        user,
                        request.getToken(),
                        request.getPlatform()
                ));

        notificationTokenRepository.save(notificationToken);
    }
}