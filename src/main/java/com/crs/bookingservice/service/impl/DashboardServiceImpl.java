package com.crs.bookingservice.service.impl;

import com.crs.bookingservice.client.IamServiceClient;
import com.crs.bookingservice.client.dto.IamUserDto;
import com.crs.bookingservice.dto.response.ChartDataPointResponse;
import com.crs.bookingservice.dto.response.DashboardOverviewResponse;
import com.crs.bookingservice.dto.response.RecentActivityResponse;
import com.crs.bookingservice.entity.RentalGroup;
import com.crs.bookingservice.repository.RentalGroupRepository;
import com.crs.bookingservice.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardServiceImpl implements DashboardService {

    private final RentalGroupRepository rentalGroupRepository;
    private final IamServiceClient iamServiceClient;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    @Transactional(readOnly = true)
    public DashboardOverviewResponse getOverview(String period) {
        long pendingBookings = rentalGroupRepository.countPendingBookings();

        // "all" → không lọc ngày, trả về tổng tất cả
        if ("all".equalsIgnoreCase(period)) {
            BigDecimal totalRevenue = rentalGroupRepository.sumRevenueSince(LocalDateTime.of(2000, 1, 1, 0, 0));
            long totalTrips = rentalGroupRepository.countTripsSince(LocalDateTime.of(2000, 1, 1, 0, 0));
            return DashboardOverviewResponse.builder()
                    .totalRevenue(totalRevenue)
                    .totalTrips(totalTrips)
                    .revenueChangePercent(0)
                    .tripsChangePercent(0)
                    .pendingBookings(pendingBookings)
                    .build();
        }

        int days = parseDays(period);
        LocalDateTime currentStart  = LocalDateTime.now().minusDays(days);
        LocalDateTime previousStart = currentStart.minusDays(days);

        BigDecimal currentRevenue  = rentalGroupRepository.sumRevenueSince(currentStart);
        BigDecimal previousRevenue = rentalGroupRepository.sumRevenueSince(previousStart)
                                        .subtract(currentRevenue);

        long currentTrips  = rentalGroupRepository.countTripsSince(currentStart);
        long previousTrips = rentalGroupRepository.countTripsSince(previousStart) - currentTrips;

        double revenueChange = calcChangePercent(previousRevenue, currentRevenue);
        double tripsChange   = calcChangePercent(BigDecimal.valueOf(previousTrips), BigDecimal.valueOf(currentTrips));

        return DashboardOverviewResponse.builder()
                .totalRevenue(currentRevenue)
                .totalTrips(currentTrips)
                .revenueChangePercent(revenueChange)
                .tripsChangePercent(tripsChange)
                .pendingBookings(pendingBookings)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChartDataPointResponse> getChartData(String period) {
        // "all" → lấy 30 ngày gần nhất để hiển thị biểu đồ
        if ("all".equalsIgnoreCase(period)) {
            return getChartData("30d");
        }
        int days = parseDays(period);
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        List<Object[]> rawData = rentalGroupRepository.getDailyStats(since);

        // Build map: dateStr → [revenue, bookings]
        Map<String, long[]> dataMap = new HashMap<>();
        for (Object[] row : rawData) {
            String dateStr = (String) row[0];
            long revenue = ((Number) row[1]).longValue();
            long count = ((Number) row[2]).longValue();
            dataMap.put(dateStr, new long[]{revenue, count});
        }

        List<ChartDataPointResponse> result = new ArrayList<>();

        if (days == 7) {
            // Điểm theo từng ngày trong 7 ngày qua
            for (int i = days - 1; i >= 0; i--) {
                LocalDate date = LocalDate.now().minusDays(i);
                String dateStr = date.format(DATE_FMT);
                long[] vals = dataMap.getOrDefault(dateStr, new long[]{0, 0});
                result.add(ChartDataPointResponse.builder()
                        .name(getViDayName(date.getDayOfWeek()))
                        .revenue(vals[0])
                        .bookings((int) vals[1])
                        .build());
            }
        } else {
            // 30 ngày → nhóm thành 4 tuần
            for (int week = 0; week < 4; week++) {
                int daysAgoEnd = (3 - week) * 7;
                int daysAgoStart = daysAgoEnd + 6;
                LocalDate weekEnd = LocalDate.now().minusDays(daysAgoEnd);
                LocalDate weekStart = LocalDate.now().minusDays(daysAgoStart);

                long weekRevenue = 0;
                long weekBookings = 0;
                for (LocalDate d = weekStart; !d.isAfter(weekEnd); d = d.plusDays(1)) {
                    long[] vals = dataMap.getOrDefault(d.format(DATE_FMT), new long[]{0, 0});
                    weekRevenue += vals[0];
                    weekBookings += vals[1];
                }
                result.add(ChartDataPointResponse.builder()
                        .name("Tuần " + (week + 1))
                        .revenue(weekRevenue)
                        .bookings((int) weekBookings)
                        .build());
            }
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecentActivityResponse> getRecentActivities() {
        List<RentalGroup> recentGroups = rentalGroupRepository.findTop10ByOrderByCreatedAtDesc();

        List<RecentActivityResponse> result = new ArrayList<>();
        for (RentalGroup rg : recentGroups) {
            String customerName = null;
            try {
                IamUserDto user = iamServiceClient.getUserById(rg.getUserId());
                if (user != null) customerName = user.getFullName();
            } catch (Exception e) {
                log.warn("[Feign] Không lấy được tên khách hàng cho userId {}: {}", rg.getUserId(), e.getMessage());
            }

            result.add(RecentActivityResponse.builder()
                    .id(rg.getId())
                    .bookingCode(rg.getBookingCode())
                    .userId(rg.getUserId())
                    .customerName(customerName)
                    .status(rg.getStatus())
                    .totalAmount(rg.getTotalAmount())
                    .createdAt(rg.getCreatedAt())
                    .build());
        }
        return result;
    }

    // ================================================================
    // HELPERS
    // ================================================================

    private int parseDays(String period) {
        if ("30d".equalsIgnoreCase(period)) return 30;
        return 7;
    }

    private double calcChangePercent(BigDecimal previous, BigDecimal current) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return current != null && current.compareTo(BigDecimal.ZERO) > 0 ? 100.0 : 0.0;
        }
        return current.subtract(previous)
                .divide(previous, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    private String getViDayName(DayOfWeek dow) {
        return switch (dow) {
            case MONDAY -> "T2";
            case TUESDAY -> "T3";
            case WEDNESDAY -> "T4";
            case THURSDAY -> "T5";
            case FRIDAY -> "T6";
            case SATURDAY -> "T7";
            case SUNDAY -> "CN";
        };
    }
}
