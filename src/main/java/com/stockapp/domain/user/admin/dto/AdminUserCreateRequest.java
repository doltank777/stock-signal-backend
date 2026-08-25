package com.stockapp.domain.user.admin.dto;

import com.stockapp.domain.user.MembershipType;
import com.stockapp.domain.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.time.Instant;

@Getter
public class AdminUserCreateRequest {

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 100, message = "이메일은 100자를 초과할 수 없습니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;

    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(max = 50, message = "닉네임은 50자를 초과할 수 없습니다.")
    private String nickname;

    @NotBlank(message = "전화번호는 필수입니다.")
    private String phoneNumber;

    @NotNull(message = "Role은 필수입니다.")
    private UserRole role;

    @NotNull(message = "회원 등급은 필수입니다.")
    private MembershipType membershipType;

    private Instant membershipStartedAt;

    private Instant membershipExpiredAt;
}
