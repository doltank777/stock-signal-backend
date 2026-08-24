package com.stockapp.domain.screening.admin;

import com.stockapp.domain.screening.admin.dto.AdminOperationalRealtimeRecoveryResponse;
import com.stockapp.domain.screening.admin.dto.AdminOperationalRealtimeStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/operational-realtime")
@RequiredArgsConstructor
public class AdminOperationalRealtimeController {

    private final AdminOperationalRealtimeService service;

    @GetMapping("/status")
    public AdminOperationalRealtimeStatusResponse getStatus() {
        return service.getStatus();
    }

    @PostMapping("/reconciliation/retry")
    public AdminOperationalRealtimeRecoveryResponse retryPendingReconciliation() {
        return service.retryPendingReconciliation();
    }
}
