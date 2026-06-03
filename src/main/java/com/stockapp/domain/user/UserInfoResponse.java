package com.stockapp.domain.user;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserInfoResponse {

    private String email;
    private String nickname;
    private String role;

    public static UserInfoResponse from(User user) {
        return UserInfoResponse.builder()
                .email(user.getEmail())
                .nickname(user.getNickname())
                .role(user.getRole().name())
                .build();
    }
}