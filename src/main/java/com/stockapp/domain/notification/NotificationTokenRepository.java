package com.stockapp.domain.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationTokenRepository extends JpaRepository<NotificationToken, Long> {

    Optional<NotificationToken> findByToken(String token);

    boolean existsByUserIdAndToken(Long userId, String token);
}