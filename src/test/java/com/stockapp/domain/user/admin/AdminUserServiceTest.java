package com.stockapp.domain.user.admin;

import com.stockapp.domain.notification.NotificationTokenRepository;
import com.stockapp.domain.user.User;
import com.stockapp.domain.user.UserRepository;
import com.stockapp.domain.user.UserRole;
import com.stockapp.domain.user.admin.dto.AdminUserDetailResponse;
import com.stockapp.domain.user.admin.dto.AdminUserPageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-18T00:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationTokenRepository notificationTokenRepository;

    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(
                userRepository,
                notificationTokenRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void returnsPageAndLoadsPushRegistrationInOneBatch() {
        User first = createUser(1L, "first@example.com");
        User second = createUser(2L, "second@example.com");
        when(userRepository.findAdminUsers(eq("member"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second)));
        when(notificationTokenRepository.findUserIdsWithToken(List.of(1L, 2L)))
                .thenReturn(List.of(2L));

        AdminUserPageResponse response = adminUserService.getUsers(
                AdminMembershipFilter.ALL,
                "  member  ",
                0,
                20
        );

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent().get(0).isPushRegistered()).isFalse();
        assertThat(response.getContent().get(1).isPushRegistered()).isTrue();
        verify(notificationTokenRepository).findUserIdsWithToken(List.of(1L, 2L));
    }

    @Test
    void convertsBlankKeywordToNoSearchCondition() {
        when(userRepository.findAdminUsers(eq(null), any(Pageable.class)))
                .thenReturn(Page.empty());

        adminUserService.getUsers(AdminMembershipFilter.ALL, "   ", 0, 20);

        verify(notificationTokenRepository, never()).findUserIdsWithToken(any());
    }

    @Test
    void rejectsInvalidPagination() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> adminUserService.getUsers(AdminMembershipFilter.ALL, null, -1, 20));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> adminUserService.getUsers(AdminMembershipFilter.ALL, null, 0, 0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> adminUserService.getUsers(AdminMembershipFilter.ALL, null, 0, 101));
    }

    @Test
    void returnsUserDetailWithPushTokenCount() {
        User user = createUser(1L, "user@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(notificationTokenRepository.countByUserId(1L)).thenReturn(2L);

        AdminUserDetailResponse response = adminUserService.getUser(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.isPushRegistered()).isTrue();
        assertThat(response.getPushTokenCount()).isEqualTo(2);
    }

    @Test
    void returnsUserDetailWithoutPushToken() {
        User user = createUser(1L, "user@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        AdminUserDetailResponse response = adminUserService.getUser(1L);

        assertThat(response.isPushRegistered()).isFalse();
        assertThat(response.getPushTokenCount()).isZero();
    }

    @Test
    void rejectsMissingUserDetail() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> adminUserService.getUser(999L))
                .withMessage("사용자를 찾을 수 없습니다.");
    }

    private User createUser(Long id, String email) {
        return User.builder()
                .id(id)
                .email(email)
                .password("encoded-password")
                .nickname("user")
                .phoneNumber("encrypted-phone")
                .role(UserRole.USER)
                .build();
    }
}
