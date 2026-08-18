package com.stockapp.domain.user;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class UserTest {

    private static final Instant STARTED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant EXPIRED_AT = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    void createsUserWithFreeMembership() {
        User user = createUser();

        assertThat(user.getMembershipType()).isEqualTo(MembershipType.FREE);
        assertThat(user.getMembershipStartedAt()).isNull();
        assertThat(user.getMembershipExpiredAt()).isNull();
        assertThat(user.isPaidActive(STARTED_AT)).isFalse();
    }

    @Test
    void changesMembershipToPaid() {
        User user = createUser();

        user.changeToPaid(STARTED_AT, EXPIRED_AT);

        assertThat(user.getMembershipType()).isEqualTo(MembershipType.PAID);
        assertThat(user.getMembershipStartedAt()).isEqualTo(STARTED_AT);
        assertThat(user.getMembershipExpiredAt()).isEqualTo(EXPIRED_AT);
    }

    @Test
    void changesPaidMembershipToFreeAndClearsPeriod() {
        User user = createUser();
        user.changeToPaid(STARTED_AT, EXPIRED_AT);

        user.changeToFree();

        assertThat(user.getMembershipType()).isEqualTo(MembershipType.FREE);
        assertThat(user.getMembershipStartedAt()).isNull();
        assertThat(user.getMembershipExpiredAt()).isNull();
    }

    @Test
    void rejectsInvalidPaidMembershipPeriod() {
        User user = createUser();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> user.changeToPaid(null, EXPIRED_AT));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> user.changeToPaid(STARTED_AT, null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> user.changeToPaid(STARTED_AT, STARTED_AT));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> user.changeToPaid(EXPIRED_AT, STARTED_AT));
    }

    @Test
    void evaluatesPaidMembershipAtExactBoundaries() {
        User user = createUser();
        user.changeToPaid(STARTED_AT, EXPIRED_AT);

        assertThat(user.isPaidActive(STARTED_AT.minusNanos(1))).isFalse();
        assertThat(user.isPaidActive(STARTED_AT)).isTrue();
        assertThat(user.isPaidActive(STARTED_AT.plusSeconds(1))).isTrue();
        assertThat(user.isPaidActive(EXPIRED_AT)).isFalse();
        assertThat(user.isPaidActive(EXPIRED_AT.plusNanos(1))).isFalse();
        assertThat(user.getMembershipType()).isEqualTo(MembershipType.PAID);
    }

    @Test
    void allowsFuturePaidMembershipButKeepsItInactive() {
        User user = createUser();
        user.changeToPaid(STARTED_AT, EXPIRED_AT);

        assertThat(user.isPaidActive(STARTED_AT.minusSeconds(1))).isFalse();
        assertThat(user.getMembershipType()).isEqualTo(MembershipType.PAID);
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
