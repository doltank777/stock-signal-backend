package com.stockapp.domain.user;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(nullable = false, length = 255)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "membership_type", nullable = false, length = 20)
    private MembershipType membershipType;

    @Column(name = "membership_started_at")
    private Instant membershipStartedAt;

    @Column(name = "membership_expired_at")
    private Instant membershipExpiredAt;

    @Builder
    private User(
            Long id,
            String email,
            String password,
            String nickname,
            String phoneNumber,
            UserRole role,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.phoneNumber = phoneNumber;
        this.role = role;
        this.createdAt = createdAt;
        changeToFree();
    }

    public void changeToFree() {
        this.membershipType = MembershipType.FREE;
        this.membershipStartedAt = null;
        this.membershipExpiredAt = null;
    }

    public void changeToPaid(
            Instant startedAt,
            Instant expiredAt
    ) {
        if (startedAt == null) {
            throw new IllegalArgumentException("유료회원 시작 시각은 필수입니다.");
        }

        if (expiredAt == null) {
            throw new IllegalArgumentException("유료회원 만료 시각은 필수입니다.");
        }

        if (!expiredAt.isAfter(startedAt)) {
            throw new IllegalArgumentException("유료회원 만료 시각은 시작 시각보다 이후여야 합니다.");
        }

        this.membershipType = MembershipType.PAID;
        this.membershipStartedAt = startedAt;
        this.membershipExpiredAt = expiredAt;
    }

    public boolean isPaidActive(Instant now) {
        Objects.requireNonNull(now, "현재 시각은 필수입니다.");

        return this.membershipType == MembershipType.PAID
                && !this.membershipStartedAt.isAfter(now)
                && this.membershipExpiredAt.isAfter(now);
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();

        if (this.role == null) {
            this.role = UserRole.USER;
        }
    }
}
