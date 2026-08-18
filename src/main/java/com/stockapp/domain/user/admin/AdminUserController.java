package com.stockapp.domain.user.admin;

import com.stockapp.domain.user.admin.dto.AdminUserDetailResponse;
import com.stockapp.domain.user.admin.dto.AdminUserPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public AdminUserPageResponse getUsers(
            @RequestParam(defaultValue = "ALL") AdminMembershipFilter membership,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return adminUserService.getUsers(membership, keyword, page, size);
    }

    @GetMapping("/{id}")
    public AdminUserDetailResponse getUser(@PathVariable Long id) {
        return adminUserService.getUser(id);
    }
}
