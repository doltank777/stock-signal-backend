package com.stockapp.domain.notification;

import com.stockapp.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface NotificationTokenRepository extends JpaRepository<NotificationToken, Long> {

    Optional<NotificationToken> findByToken(String token);

    boolean existsByUserIdAndToken(Long userId, String token);

    List<NotificationToken> findByUser(User user);
}