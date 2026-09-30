package com.isd.wms.controller;

import com.isd.wms.dto.dashboard.SupervisorDashboardResponse;
import com.isd.wms.service.SupervisorDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the supervisor dashboard.
 *
 * <p>Provides an aggregated snapshot of key warehouse metrics for supervisors,
 * including order status, operator activity, stock levels, and replenishment
 * summaries. Access is restricted to users with the {@code SUPERVISOR} or
 * {@code DEV} role.</p>
 *
 * <p>Base path: {@code /api/supervisor/dashboard}</p>
 */
@RestController
@RequestMapping("/api/supervisor/dashboard")
@RequiredArgsConstructor
public class SupervisorDashboardController {

    private final SupervisorDashboardService supervisorDashboardService;

    /**
     * Retrieves the aggregated supervisor dashboard data.
     *
     * @return {@code 200 OK} with a {@link SupervisorDashboardResponse} containing key warehouse metrics
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<SupervisorDashboardResponse> getDashboard() {
        return ResponseEntity.ok(supervisorDashboardService.getDashboard());
    }
}
