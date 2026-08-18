package com.stockapp.domain.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesAndLoadsFreeMembership() {
        User savedUser = userRepository.saveAndFlush(createUser("free@example.com"));

        User loadedUser = userRepository.findById(savedUser.getId()).orElseThrow();

        assertThat(loadedUser.getMembershipType()).isEqualTo(MembershipType.FREE);
        assertThat(loadedUser.getMembershipStartedAt()).isNull();
        assertThat(loadedUser.getMembershipExpiredAt()).isNull();
    }

    @Test
    void savesAndLoadsPaidMembershipPeriodAsInstant() {
        Instant startedAt = Instant.parse("2026-08-01T00:00:00.123456Z");
        Instant expiredAt = Instant.parse("2026-09-01T00:00:00.654321Z");
        User user = createUser("paid@example.com");
        user.changeToPaid(startedAt, expiredAt);

        User savedUser = userRepository.saveAndFlush(user);
        User loadedUser = userRepository.findById(savedUser.getId()).orElseThrow();

        assertThat(loadedUser.getMembershipType()).isEqualTo(MembershipType.PAID);
        assertThat(loadedUser.getMembershipStartedAt()).isEqualTo(startedAt);
        assertThat(loadedUser.getMembershipExpiredAt()).isEqualTo(expiredAt);
    }

    private User createUser(String email) {
        return User.builder()
                .email(email)
                .password("encoded-password")
                .nickname("user")
                .phoneNumber("encrypted-phone")
                .role(UserRole.USER)
                .build();
    }
}
