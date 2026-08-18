package com.stockapp.domain.user.admin;

import com.stockapp.domain.user.User;
import com.stockapp.domain.user.UserRole;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AdminMembershipStatusTest {

    private static final Instant NOW = Instant.parse("2026-08-18T00:00:00Z");

    @Test
    void resolvesFreeStatus() {
        assertThat(AdminMembershipStatus.from(createUser(), NOW))
                .isEqualTo(AdminMembershipStatus.FREE);
    }

    @Test
    void resolvesScheduledStatusBeforeStart() {
        User user = createUser();
        user.changeToPaid(NOW.plusSeconds(1), NOW.plusSeconds(2));

        assertThat(AdminMembershipStatus.from(user, NOW))
                .isEqualTo(AdminMembershipStatus.PAID_SCHEDULED);
    }

    @Test
    void resolvesActiveStatusAtStartBoundary() {
        User user = createUser();
        user.changeToPaid(NOW, NOW.plusSeconds(1));

        assertThat(AdminMembershipStatus.from(user, NOW))
                .isEqualTo(AdminMembershipStatus.PAID_ACTIVE);
    }

    @Test
    void resolvesExpiredStatusAtExpirationBoundary() {
        User user = createUser();
        user.changeToPaid(NOW.minusSeconds(1), NOW);

        assertThat(AdminMembershipStatus.from(user, NOW))
                .isEqualTo(AdminMembershipStatus.PAID_EXPIRED);
    }

    private User createUser() {
        return User.builder()
                .email("user@example.com")
                .password("encoded-password")
                .nickname("user")
                .phoneNumber("encrypted-phone")
                .role(UserRole.USER)
                .build();
    }
}
