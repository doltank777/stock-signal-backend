package com.stockapp.domain.screening.admin;

import com.stockapp.domain.screening.admin.dto.AdminRealtimeWatchStatusResponse;
import com.stockapp.domain.screening.admin.dto.AdminScreeningResultsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminScreeningDashboardController {

    private final AdminScreeningDashboardService dashboardService;

    @GetMapping("/screening-results")
    public AdminScreeningResultsResponse getScreeningResults() {
        return dashboardService.getScreeningResults();
    }

    @GetMapping("/realtime-watch-targets")
    public AdminRealtimeWatchStatusResponse getRealtimeWatchTargets() {
        return dashboardService.getRealtimeWatchStatus();
    }
}
