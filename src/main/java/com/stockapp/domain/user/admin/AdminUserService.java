package com.stockapp.domain.user.admin;

import com.stockapp.domain.notification.NotificationTokenRepository;
import com.stockapp.domain.user.User;
import com.stockapp.domain.user.UserRepository;
import com.stockapp.domain.user.MembershipType;
import com.stockapp.domain.user.admin.dto.AdminUserDetailResponse;
import com.stockapp.domain.user.admin.dto.AdminUserListItemResponse;
import com.stockapp.domain.user.admin.dto.AdminUserMembershipUpdateRequest;
import com.stockapp.domain.user.admin.dto.AdminUserPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final NotificationTokenRepository notificationTokenRepository;
    private final Clock clock;

    public AdminUserPageResponse getUsers(
            AdminMembershipFilter membership,
            String keyword,
            int page,
            int size
    ) {
        validatePage(page, size);

        Instant now = Instant.now(clock);
        String normalizedKeyword = normalizeKeyword(keyword);
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );
        Page<User> userPage = findUsers(
                membership,
                normalizedKeyword,
                now,
                pageable
        );
        List<Long> userIds = userPage.getContent()
                .stream()
                .map(User::getId)
                .toList();
        Set<Long> pushRegisteredUserIds = getPushRegisteredUserIds(userIds);
        List<AdminUserListItemResponse> content = userPage.getContent()
                .stream()
                .map(user -> AdminUserListItemResponse.from(
                        user,
                        now,
                        pushRegisteredUserIds.contains(user.getId())
                ))
                .toList();

        return AdminUserPageResponse.builder()
                .content(content)
                .page(userPage.getNumber())
                .size(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .build();
    }

    public AdminUserDetailResponse getUser(Long id) {
        User user = findUser(id);
        Instant now = Instant.now(clock);
        long pushTokenCount = notificationTokenRepository.countByUserId(id);

        return AdminUserDetailResponse.from(user, now, pushTokenCount);
    }

    @Transactional
    public AdminUserDetailResponse updateMembership(
            Long id,
            AdminUserMembershipUpdateRequest request
    ) {
        validateMembershipRequest(request);

        User user = findUser(id);

        if (request.getMembershipType() == MembershipType.FREE) {
            user.changeToFree();
        } else {
            user.changeToPaid(
                    request.getMembershipStartedAt(),
                    request.getMembershipExpiredAt()
            );
        }

        Instant now = Instant.now(clock);
        long pushTokenCount = notificationTokenRepository.countByUserId(id);

        return AdminUserDetailResponse.from(user, now, pushTokenCount);
    }

    private Set<Long> getPushRegisteredUserIds(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }

        return new HashSet<>(notificationTokenRepository.findUserIdsWithToken(userIds));
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
    }

    private void validateMembershipRequest(AdminUserMembershipUpdateRequest request) {
        if (request.getMembershipType() == null) {
            throw new IllegalArgumentException("회원 등급은 필수입니다.");
        }

        Instant startedAt = request.getMembershipStartedAt();
        Instant expiredAt = request.getMembershipExpiredAt();

        if (request.getMembershipType() == MembershipType.FREE) {
            if (startedAt != null || expiredAt != null) {
                throw new IllegalArgumentException("FREE 회원에는 유료기간을 지정할 수 없습니다.");
            }

            return;
        }

        if (startedAt == null) {
            throw new IllegalArgumentException("유료회원 시작 시각은 필수입니다.");
        }

        if (expiredAt == null) {
            throw new IllegalArgumentException("유료회원 만료 시각은 필수입니다.");
        }

        if (!expiredAt.isAfter(startedAt)) {
            throw new IllegalArgumentException("유료회원 만료 시각은 시작 시각보다 이후여야 합니다.");
        }
    }

    private Page<User> findUsers(
            AdminMembershipFilter membership,
            String keyword,
            Instant now,
            PageRequest pageable
    ) {
        return switch (membership) {
            case ALL -> userRepository.findAdminUsers(keyword, pageable);
            case FREE -> userRepository.findAdminFreeUsers(keyword, pageable);
            case PAID_SCHEDULED -> userRepository.findAdminScheduledPaidUsers(keyword, now, pageable);
            case PAID_ACTIVE -> userRepository.findAdminActivePaidUsers(keyword, now, pageable);
            case PAID_EXPIRED -> userRepository.findAdminExpiredPaidUsers(keyword, now, pageable);
        };
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim();
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("페이지 번호는 0 이상이어야 합니다.");
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("페이지 크기는 1 이상 100 이하여야 합니다.");
        }
    }
}
