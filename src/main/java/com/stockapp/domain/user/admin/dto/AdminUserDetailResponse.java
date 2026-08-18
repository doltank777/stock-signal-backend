package com.stockapp.domain.user.admin.dto;

import com.stockapp.domain.user.MembershipType;
import com.stockapp.domain.user.User;
import com.stockapp.domain.user.UserRole;
import com.stockapp.domain.user.admin.AdminMembershipStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@Builder
public class AdminUserDetailResponse {

    private Long id;
    private String email;
    private String nickname;
    private UserRole role;
    private MembershipType membershipType;
    private AdminMembershipStatus membershipStatus;
    private Instant membershipStartedAt;
    private Instant membershipExpiredAt;
    private LocalDateTime createdAt;
    private boolean pushRegistered;
    private long pushTokenCount;

    public static AdminUserDetailResponse from(
            User user,
            Instant now,
            long pushTokenCount
    ) {
        return AdminUserDetailResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .role(user.getRole())
                .membershipType(user.getMembershipType())
                .membershipStatus(AdminMembershipStatus.from(user, now))
                .membershipStartedAt(user.getMembershipStartedAt())
                .membershipExpiredAt(user.getMembershipExpiredAt())
                .createdAt(user.getCreatedAt())
                .pushRegistered(pushTokenCount > 0)
                .pushTokenCount(pushTokenCount)
                .build();
    }
}
