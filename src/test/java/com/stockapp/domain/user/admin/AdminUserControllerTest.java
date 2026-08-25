package com.stockapp.domain.user.admin;

import com.stockapp.domain.user.admin.dto.AdminUserCreateRequest;
import com.stockapp.domain.user.admin.dto.AdminUserDetailResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserControllerTest {

    @Test
    void createsUserThroughAdminServiceAndDeclaresCreatedStatus()
            throws NoSuchMethodException {
        AdminUserService service = mock(AdminUserService.class);
        AdminUserCreateRequest request = mock(AdminUserCreateRequest.class);
        AdminUserDetailResponse response =
                mock(AdminUserDetailResponse.class);
        when(service.createUser(request)).thenReturn(response);
        AdminUserController controller = new AdminUserController(service);

        assertThat(controller.createUser(request)).isSameAs(response);
        verify(service).createUser(request);

        ResponseStatus status = AdminUserController.class
                .getMethod("createUser", AdminUserCreateRequest.class)
                .getAnnotation(ResponseStatus.class);
        assertThat(status.value()).isEqualTo(HttpStatus.CREATED);
    }
}
