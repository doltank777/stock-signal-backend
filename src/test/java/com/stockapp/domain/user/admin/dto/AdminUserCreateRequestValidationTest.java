package com.stockapp.domain.user.admin.dto;

import com.stockapp.domain.user.MembershipType;
import com.stockapp.domain.user.UserRole;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AdminUserCreateRequestValidationTest {

    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void acceptsCompleteCreateRequest() {
        assertThat(validator.validate(validRequest())).isEmpty();
    }

    @Test
    void rejectsInvalidEmailAndMissingRequiredValues() {
        AdminUserCreateRequest request = validRequest();
        set(request, "email", "invalid-email");
        set(request, "password", "");
        set(request, "nickname", "");
        set(request, "phoneNumber", "");
        set(request, "role", null);
        set(request, "membershipType", null);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("email", "password", "nickname", "phoneNumber",
                        "role", "membershipType");
    }

    private AdminUserCreateRequest validRequest() {
        AdminUserCreateRequest request = new AdminUserCreateRequest();
        set(request, "email", "user@example.com");
        set(request, "password", "password");
        set(request, "nickname", "nickname");
        set(request, "phoneNumber", "01012345678");
        set(request, "role", UserRole.USER);
        set(request, "membershipType", MembershipType.PAID);
        set(request, "membershipStartedAt", Instant.parse("2026-08-25T00:00:00Z"));
        set(request, "membershipExpiredAt", Instant.parse("2026-09-25T00:00:00Z"));
        return request;
    }

    private void set(
            AdminUserCreateRequest request, String field, Object value
    ) {
        ReflectionTestUtils.setField(request, field, value);
    }
}
