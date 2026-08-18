package com.stockapp.domain.user.admin;

import com.stockapp.domain.notification.NotificationToken;
import com.stockapp.domain.notification.NotificationTokenRepository;
import com.stockapp.domain.user.User;
import com.stockapp.domain.user.UserRepository;
import com.stockapp.domain.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AdminUserRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-08-18T00:00:00Z");
    private static final PageRequest PAGEABLE = PageRequest.of(
            0,
            20,
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
    );

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationTokenRepository notificationTokenRepository;

    private User freeUser;
    private User scheduledUser;
    private User activeUser;
    private User expiredUser;

    @BeforeEach
    void setUp() {
        freeUser = saveUser("free@example.com", "무료회원");
        scheduledUser = savePaidUser(
                "scheduled@example.com",
                "예약회원",
                NOW.plusSeconds(1),
                NOW.plusSeconds(100)
        );
        activeUser = savePaidUser(
                "active@example.com",
                "활성회원",
                NOW,
                NOW.plusSeconds(100)
        );
        expiredUser = savePaidUser(
                "expired@example.com",
                "만료회원",
                NOW.minusSeconds(100),
                NOW
        );
    }

    @Test
    void filtersAllMembershipStatuses() {
        assertThat(find(AdminMembershipFilter.ALL, null).getTotalElements()).isEqualTo(4);
        assertThat(find(AdminMembershipFilter.FREE, null).getContent())
                .extracting(User::getId)
                .containsExactly(freeUser.getId());
        assertThat(find(AdminMembershipFilter.PAID_SCHEDULED, null).getContent())
                .extracting(User::getId)
                .containsExactly(scheduledUser.getId());
        assertThat(find(AdminMembershipFilter.PAID_ACTIVE, null).getContent())
                .extracting(User::getId)
                .containsExactly(activeUser.getId());
        assertThat(find(AdminMembershipFilter.PAID_EXPIRED, null).getContent())
                .extracting(User::getId)
                .containsExactly(expiredUser.getId());
    }

    @Test
    void searchesEmailAndNicknameAndCombinesMembershipFilter() {
        assertThat(find(AdminMembershipFilter.ALL, "active@").getContent())
                .extracting(User::getId)
                .containsExactly(activeUser.getId());
        assertThat(find(AdminMembershipFilter.FREE, "무료").getContent())
                .extracting(User::getId)
                .containsExactly(freeUser.getId());
        assertThat(find(AdminMembershipFilter.PAID_ACTIVE, "예약").getContent()).isEmpty();
        assertThat(find(AdminMembershipFilter.ALL, "없는회원").getContent()).isEmpty();
    }

    @Test
    void appliesPaginationAndDeterministicSort() {
        Page<User> firstPage = userRepository.findAdminUsers(
                null,
                PageRequest.of(0, 2, PAGEABLE.getSort())
        );

        assertThat(firstPage.getTotalElements()).isEqualTo(4);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getContent())
                .extracting(User::getId)
                .isSortedAccordingTo((first, second) -> Long.compare(second, first));
    }

    @Test
    void findsPushRegisteredUsersInOneDistinctQuery() {
        notificationTokenRepository.saveAll(List.of(
                new NotificationToken(activeUser, "ExponentPushToken[first]", "android"),
                new NotificationToken(activeUser, "ExponentPushToken[second]", "ios")
        ));

        List<Long> userIds = notificationTokenRepository.findUserIdsWithToken(
                List.of(freeUser.getId(), activeUser.getId())
        );

        assertThat(userIds).containsExactly(activeUser.getId());
        assertThat(notificationTokenRepository.countByUserId(activeUser.getId())).isEqualTo(2);
    }

    private Page<User> find(AdminMembershipFilter filter, String keyword) {
        return switch (filter) {
            case ALL -> userRepository.findAdminUsers(keyword, PAGEABLE);
            case FREE -> userRepository.findAdminFreeUsers(keyword, PAGEABLE);
            case PAID_SCHEDULED -> userRepository.findAdminScheduledPaidUsers(keyword, NOW, PAGEABLE);
            case PAID_ACTIVE -> userRepository.findAdminActivePaidUsers(keyword, NOW, PAGEABLE);
            case PAID_EXPIRED -> userRepository.findAdminExpiredPaidUsers(keyword, NOW, PAGEABLE);
        };
    }

    private User savePaidUser(
            String email,
            String nickname,
            Instant startedAt,
            Instant expiredAt
    ) {
        User user = createUser(email, nickname);
        user.changeToPaid(startedAt, expiredAt);
        return userRepository.saveAndFlush(user);
    }

    private User saveUser(String email, String nickname) {
        return userRepository.saveAndFlush(createUser(email, nickname));
    }

    private User createUser(String email, String nickname) {
        return User.builder()
                .email(email)
                .password("encoded-password")
                .nickname(nickname)
                .phoneNumber("encrypted-phone")
                .role(UserRole.USER)
                .build();
    }
}
