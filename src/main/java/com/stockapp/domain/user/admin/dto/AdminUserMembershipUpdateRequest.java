package com.stockapp.domain.user.admin.dto;

import com.stockapp.domain.user.MembershipType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.Instant;

@Getter
public class AdminUserMembershipUpdateRequest {

    @NotNull(message = "회원 등급은 필수입니다.")
    private MembershipType membershipType;

    private Instant membershipStartedAt;

    private Instant membershipExpiredAt;
}
