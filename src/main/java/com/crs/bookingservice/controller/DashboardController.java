package com.crs.bookingservice.controller;

import com.crs.bookingservice.dto.response.ApiResponse;
import com.crs.bookingservice.dto.response.ChartDataPointResponse;
import com.crs.bookingservice.dto.response.DashboardOverviewResponse;
import com.crs.bookingservice.dto.response.RecentActivityResponse;
import com.crs.bookingservice.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Dashboard API — tổng hợp số liệu cho trang admin dashboard.
 * Tất cả endpoint đều đọc từ booking-service (+ enrich từ iam-service).
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Dashboard", description = "API tổng hợp dữ liệu cho admin dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    @Operation(
            summary = "Tổng quan dashboard",
            description = "Trả về tổng doanh thu, tổng chuyến, % thay đổi so với kỳ trước, số booking đang chờ"
    )
    public ResponseEntity<ApiResponse<DashboardOverviewResponse>> getOverview(
            @RequestParam(defaultValue = "7d") String period) {

        log.info("REST request get dashboard overview, period={}", period);
        DashboardOverviewResponse overview = dashboardService.getOverview(period);
        return ResponseEntity.ok(ApiResponse.success(overview, "Dashboard overview retrieved"));
    }

    @GetMapping("/chart")
    @Operation(
            summary = "Dữ liệu biểu đồ doanh thu",
            description = "7d → 7 điểm theo ngày (T2–CN) | 30d → 4 điểm theo tuần (Tuần 1–4)"
    )
    public ResponseEntity<ApiResponse<List<ChartDataPointResponse>>> getChartData(
            @RequestParam(defaultValue = "7d") String period) {

        log.info("REST request get dashboard chart data, period={}", period);
        List<ChartDataPointResponse> data = dashboardService.getChartData(period);
        return ResponseEntity.ok(ApiResponse.success(data, "Chart data retrieved"));
    }

    @GetMapping("/recent")
    @Operation(
            summary = "Hoạt động mới nhất",
            description = "10 booking mới nhất kèm tên khách hàng (enrich từ iam-service)"
    )
    public ResponseEntity<ApiResponse<List<RecentActivityResponse>>> getRecentActivities() {

        log.info("REST request get recent activities");
        List<RecentActivityResponse> activities = dashboardService.getRecentActivities();
        return ResponseEntity.ok(ApiResponse.success(activities, "Recent activities retrieved"));
    }
}
