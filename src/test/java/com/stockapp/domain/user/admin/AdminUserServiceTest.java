package com.stockapp.domain.user.admin;

import com.stockapp.domain.notification.NotificationTokenRepository;
import com.stockapp.domain.user.User;
import com.stockapp.domain.user.UserRepository;
import com.stockapp.domain.user.UserRole;
import com.stockapp.domain.user.MembershipType;
import com.stockapp.domain.user.admin.dto.AdminUserDetailResponse;
import com.stockapp.domain.user.admin.dto.AdminUserCreateRequest;
import com.stockapp.domain.user.admin.dto.AdminUserMembershipUpdateRequest;
import com.stockapp.domain.user.admin.dto.AdminUserPageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.stockapp.global.util.CryptoUtil;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-18T00:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationTokenRepository notificationTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CryptoUtil cryptoUtil;

    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(
                userRepository,
                notificationTokenRepository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                passwordEncoder,
                cryptoUtil
        );
    }

    @Test
    void createsFreeUserWithEncodedPasswordAndEncryptedPhone() {
        AdminUserCreateRequest request = createRequest(
                UserRole.USER, MembershipType.FREE, null, null);
        when(passwordEncoder.encode("password"))
                .thenReturn("encoded-password");
        when(cryptoUtil.encrypt("01012345678"))
                .thenReturn("encrypted-phone");
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminUserDetailResponse response = adminUserService.createUser(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getPassword()).isEqualTo("encoded-password");
        assertThat(saved.getPhoneNumber()).isEqualTo("encrypted-phone");
        assertThat(saved.getRole()).isEqualTo(UserRole.USER);
        assertThat(saved.getMembershipType()).isEqualTo(MembershipType.FREE);
        assertThat(response.getMembershipStatus())
                .isEqualTo(AdminMembershipStatus.FREE);
    }

    @Test
    void createsAdminAndPaidUserWithRequestedMembership() {
        AdminUserCreateRequest adminRequest = createRequest(
                UserRole.ADMIN, MembershipType.FREE, null, null);
        stubCreateDependencies();
        AdminUserDetailResponse admin =
                adminUserService.createUser(adminRequest);
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);

        AdminUserCreateRequest paidRequest = createRequest(
                UserRole.USER, MembershipType.PAID,
                NOW.minusSeconds(1), NOW.plusSeconds(1));
        AdminUserDetailResponse paid =
                adminUserService.createUser(paidRequest);
        assertThat(paid.getMembershipStatus())
                .isEqualTo(AdminMembershipStatus.PAID_ACTIVE);
    }

    @Test
    void rejectsDuplicateEmailBeforeEncodingOrSaving() {
        AdminUserCreateRequest request = createRequest(
                UserRole.USER, MembershipType.FREE, null, null);
        when(userRepository.existsByEmail("user@example.com"))
                .thenReturn(true);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> adminUserService.createUser(request))
                .withMessage("이미 가입된 이메일입니다.");

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsInvalidCreateMembershipPeriodsBeforeSaving() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                adminUserService.createUser(createRequest(
                        UserRole.USER, MembershipType.PAID, null, NOW)));
        assertThatIllegalArgumentException().isThrownBy(() ->
                adminUserService.createUser(createRequest(
                        UserRole.USER, MembershipType.PAID, NOW, null)));
        assertThatIllegalArgumentException().isThrownBy(() ->
                adminUserService.createUser(createRequest(
                        UserRole.USER, MembershipType.PAID, NOW, NOW)));
        assertThatIllegalArgumentException().isThrownBy(() ->
                adminUserService.createUser(createRequest(
                        UserRole.USER, MembershipType.FREE, NOW, null)));

        verify(userRepository, never()).save(any());
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

    @Test
    void changesPaidAdminToFreeAndPreservesRole() {
        User user = createUser(1L, "admin@example.com", UserRole.ADMIN);
        user.changeToPaid(NOW.minusSeconds(10), NOW.plusSeconds(10));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(notificationTokenRepository.countByUserId(1L)).thenReturn(2L);

        AdminUserDetailResponse response = adminUserService.updateMembership(
                1L,
                request(MembershipType.FREE, null, null)
        );

        assertThat(user.getMembershipType()).isEqualTo(MembershipType.FREE);
        assertThat(user.getMembershipStartedAt()).isNull();
        assertThat(user.getMembershipExpiredAt()).isNull();
        assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(response.getMembershipStatus()).isEqualTo(AdminMembershipStatus.FREE);
        assertThat(response.getPushTokenCount()).isEqualTo(2);
    }

    @Test
    void changesFreeUserToActivePaidMembership() {
        User user = createUser(1L, "user@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        AdminUserDetailResponse response = adminUserService.updateMembership(
                1L,
                request(MembershipType.PAID, NOW.minusSeconds(1), NOW.plusSeconds(1))
        );

        assertThat(user.getMembershipType()).isEqualTo(MembershipType.PAID);
        assertThat(response.getMembershipStatus()).isEqualTo(AdminMembershipStatus.PAID_ACTIVE);
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    void allowsScheduledPaidMembership() {
        User user = createUser(1L, "user@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        AdminUserDetailResponse response = adminUserService.updateMembership(
                1L,
                request(MembershipType.PAID, NOW.plusSeconds(1), NOW.plusSeconds(2))
        );

        assertThat(response.getMembershipStatus()).isEqualTo(AdminMembershipStatus.PAID_SCHEDULED);
    }

    @Test
    void allowsExpiredPaidMembership() {
        User user = createUser(1L, "user@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        AdminUserDetailResponse response = adminUserService.updateMembership(
                1L,
                request(MembershipType.PAID, NOW.minusSeconds(2), NOW.minusSeconds(1))
        );

        assertThat(response.getMembershipStatus()).isEqualTo(AdminMembershipStatus.PAID_EXPIRED);
        assertThat(user.getMembershipType()).isEqualTo(MembershipType.PAID);
    }

    @Test
    void rejectsFreeMembershipWithAnyPeriod() {
        assertThatIllegalArgumentException().isThrownBy(() -> adminUserService.updateMembership(
                1L,
                request(MembershipType.FREE, NOW, null)
        ));
        assertThatIllegalArgumentException().isThrownBy(() -> adminUserService.updateMembership(
                1L,
                request(MembershipType.FREE, null, NOW)
        ));
        assertThatIllegalArgumentException().isThrownBy(() -> adminUserService.updateMembership(
                1L,
                request(MembershipType.FREE, NOW, NOW.plusSeconds(1))
        ));

        verify(userRepository, never()).findById(any());
    }

    @Test
    void rejectsMissingMembershipType() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> adminUserService.updateMembership(
                        1L,
                        request(null, null, null)
                ))
                .withMessage("회원 등급은 필수입니다.");

        verify(userRepository, never()).findById(any());
    }

    @Test
    void rejectsPaidMembershipWithMissingPeriod() {
        assertThatIllegalArgumentException().isThrownBy(() -> adminUserService.updateMembership(
                1L,
                request(MembershipType.PAID, null, NOW)
        ));
        assertThatIllegalArgumentException().isThrownBy(() -> adminUserService.updateMembership(
                1L,
                request(MembershipType.PAID, NOW, null)
        ));
        assertThatIllegalArgumentException().isThrownBy(() -> adminUserService.updateMembership(
                1L,
                request(MembershipType.PAID, null, null)
        ));

        verify(userRepository, never()).findById(any());
    }

    @Test
    void rejectsPaidMembershipWithInvalidPeriodOrder() {
        assertThatIllegalArgumentException().isThrownBy(() -> adminUserService.updateMembership(
                1L,
                request(MembershipType.PAID, NOW, NOW)
        ));
        assertThatIllegalArgumentException().isThrownBy(() -> adminUserService.updateMembership(
                1L,
                request(MembershipType.PAID, NOW, NOW.minusSeconds(1))
        ));

        verify(userRepository, never()).findById(any());
    }

    @Test
    void replacesExistingPaidMembershipPeriod() {
        User user = createUser(1L, "user@example.com");
        user.changeToPaid(NOW.minusSeconds(20), NOW.minusSeconds(10));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        Instant newStartedAt = NOW.minusSeconds(1);
        Instant newExpiredAt = NOW.plusSeconds(10);

        adminUserService.updateMembership(
                1L,
                request(MembershipType.PAID, newStartedAt, newExpiredAt)
        );

        assertThat(user.getMembershipStartedAt()).isEqualTo(newStartedAt);
        assertThat(user.getMembershipExpiredAt()).isEqualTo(newExpiredAt);
    }

    @Test
    void allowsIdempotentFreeMembershipUpdate() {
        User user = createUser(1L, "user@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        AdminUserDetailResponse response = adminUserService.updateMembership(
                1L,
                request(MembershipType.FREE, null, null)
        );

        assertThat(response.getMembershipStatus()).isEqualTo(AdminMembershipStatus.FREE);
        assertThat(user.getMembershipStartedAt()).isNull();
        assertThat(user.getMembershipExpiredAt()).isNull();
    }

    @Test
    void rejectsMissingUserMembershipUpdate() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> adminUserService.updateMembership(
                        999L,
                        request(MembershipType.FREE, null, null)
                ))
                .withMessage("사용자를 찾을 수 없습니다.");

        verify(notificationTokenRepository, never()).countByUserId(any());
    }

    private User createUser(Long id, String email) {
        return createUser(id, email, UserRole.USER);
    }

    private User createUser(Long id, String email, UserRole role) {
        return User.builder()
                .id(id)
                .email(email)
                .password("encoded-password")
                .nickname("user")
                .phoneNumber("encrypted-phone")
                .role(role)
                .build();
    }

    private AdminUserMembershipUpdateRequest request(
            MembershipType membershipType,
            Instant startedAt,
            Instant expiredAt
    ) {
        AdminUserMembershipUpdateRequest request = mock(AdminUserMembershipUpdateRequest.class);
        if (membershipType != null) {
            when(request.getMembershipType()).thenReturn(membershipType);
            when(request.getMembershipStartedAt()).thenReturn(startedAt);
            when(request.getMembershipExpiredAt()).thenReturn(expiredAt);
        }
        return request;
    }

    private AdminUserCreateRequest createRequest(
            UserRole role,
            MembershipType membershipType,
            Instant startedAt,
            Instant expiredAt
    ) {
        AdminUserCreateRequest request = mock(AdminUserCreateRequest.class);
        org.mockito.Mockito.lenient().when(request.getEmail())
                .thenReturn("user@example.com");
        org.mockito.Mockito.lenient().when(request.getPassword())
                .thenReturn("password");
        org.mockito.Mockito.lenient().when(request.getNickname())
                .thenReturn("nickname");
        org.mockito.Mockito.lenient().when(request.getPhoneNumber())
                .thenReturn("01012345678");
        org.mockito.Mockito.lenient().when(request.getRole()).thenReturn(role);
        org.mockito.Mockito.lenient().when(request.getMembershipType())
                .thenReturn(membershipType);
        org.mockito.Mockito.lenient().when(request.getMembershipStartedAt())
                .thenReturn(startedAt);
        org.mockito.Mockito.lenient().when(request.getMembershipExpiredAt())
                .thenReturn(expiredAt);
        return request;
    }

    private void stubCreateDependencies() {
        when(passwordEncoder.encode("password"))
                .thenReturn("encoded-password");
        when(cryptoUtil.encrypt("01012345678"))
                .thenReturn("encrypted-phone");
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }
}
