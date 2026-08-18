package com.stockapp.domain.user.admin;

import com.stockapp.domain.user.MembershipType;
import com.stockapp.domain.user.User;

import java.time.Instant;

public enum AdminMembershipStatus {
    FREE,
    PAID_SCHEDULED,
    PAID_ACTIVE,
    PAID_EXPIRED;

    public static AdminMembershipStatus from(
            User user,
            Instant now
    ) {
        MembershipType membershipType = user.getMembershipType();
        Instant startedAt = user.getMembershipStartedAt();
        Instant expiredAt = user.getMembershipExpiredAt();

        if (membershipType == MembershipType.FREE) {
            if (startedAt != null || expiredAt != null) {
                throw new IllegalStateException("FREE 회원에 유료기간이 존재합니다.");
            }

            return FREE;
        }

        if (membershipType != MembershipType.PAID
                || startedAt == null
                || expiredAt == null
                || !expiredAt.isAfter(startedAt)) {
            throw new IllegalStateException("PAID 회원의 유료기간이 올바르지 않습니다.");
        }

        if (startedAt.isAfter(now)) {
            return PAID_SCHEDULED;
        }

        if (!expiredAt.isAfter(now)) {
            return PAID_EXPIRED;
        }

        return PAID_ACTIVE;
    }
}
