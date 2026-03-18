package com.crs.bookingservice.service;

import com.crs.bookingservice.dto.response.ChartDataPointResponse;
import com.crs.bookingservice.dto.response.DashboardOverviewResponse;
import com.crs.bookingservice.dto.response.RecentActivityResponse;

import java.util.List;

public interface DashboardService {

    /**
     * Tổng quan: doanh thu, số chuyến, % thay đổi so với kỳ trước.
     * @param period "7d" hoặc "30d"
     */
    DashboardOverviewResponse getOverview(String period);

    /**
     * Dữ liệu biểu đồ doanh thu + đặt xe theo thời gian.
     * - 7d  → 7 điểm theo từng ngày trong tuần (T2–CN)
     * - 30d → 4 điểm theo từng tuần (Tuần 1–4)
     *
     * @param period "7d" hoặc "30d"
     */
    List<ChartDataPointResponse> getChartData(String period);

    /**
     * 10 booking mới nhất để hiển thị trong bảng hoạt động gần đây.
     */
    List<RecentActivityResponse> getRecentActivities();
}
