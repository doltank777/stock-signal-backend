package com.stockapp.domain.notification;

import com.stockapp.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface NotificationTokenRepository extends JpaRepository<NotificationToken, Long> {

    Optional<NotificationToken> findByToken(String token);

    boolean existsByUserIdAndToken(Long userId, String token);

    List<NotificationToken> findByUser(User user);

    @Query("""
            SELECT DISTINCT notificationToken.user.id
            FROM NotificationToken notificationToken
            WHERE notificationToken.user.id IN :userIds
            """)
    List<Long> findUserIdsWithToken(@Param("userIds") List<Long> userIds);

    long countByUserId(Long userId);
}
