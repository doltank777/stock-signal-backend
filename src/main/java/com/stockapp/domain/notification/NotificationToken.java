package com.stockapp.domain.notification;

import com.stockapp.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "notification_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 토큰 소유 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // FCM 토큰
    @Column(nullable = false, length = 500, unique = true)
    private String token;

    // android / ios
    @Column(nullable = false, length = 20)
    private String platform;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public NotificationToken(User user, String token, String platform) {
        this.user = user;
        this.token = token;
        this.platform = platform;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void update(User user, String platform) {
        this.user = user;
        this.platform = platform;
        this.updatedAt = LocalDateTime.now();
    }
}