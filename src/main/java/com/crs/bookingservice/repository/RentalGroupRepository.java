package com.crs.bookingservice.repository;

import com.crs.bookingservice.entity.RentalGroup;
import com.crs.bookingservice.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RentalGroupRepository extends JpaRepository<RentalGroup, Long> {

    Optional<RentalGroup> findByBookingCode(String bookingCode);

    boolean existsByBookingCode(String bookingCode);

    /**
     * Lấy số thứ tự lớn nhất trong ngày (phần sau dấu gạch thứ 3 của booking_code).
     * VD: BK-20260307-005 → trả về 5. Dùng để khởi tạo counter khi service restart.
     */
    @Query(value = """
            SELECT COALESCE(MAX(CAST(SPLIT_PART(booking_code, '-', 3) AS INTEGER)), 0)
            FROM rental_group
            WHERE booking_code LIKE :prefix || '-%'
            """, nativeQuery = true)
    long getMaxSequenceForDate(@Param("prefix") String prefix);

    Page<RentalGroup> findByUserId(String userId, Pageable pageable);

    Page<RentalGroup> findByStatus(BookingStatus status, Pageable pageable);

    @Query("""
            SELECT rg FROM RentalGroup rg
            WHERE (:userId IS NULL OR rg.userId = :userId)
              AND (:status IS NULL OR rg.status = :status)
            """)
    Page<RentalGroup> search(
            @Param("userId") String userId,
            @Param("status") BookingStatus status,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT rg FROM RentalGroup rg
            JOIN rg.rentalUnits ru
            WHERE ru.driver.id = :driverId
              AND (:status IS NULL OR rg.status = :status)
            """)
    Page<RentalGroup> findByDriverId(
            @Param("driverId") Long driverId,
            @Param("status") BookingStatus status,
            Pageable pageable);

    // ================================================================
    // DASHBOARD QUERIES
    // ================================================================

    @Query("""
            SELECT COALESCE(SUM(rg.totalAmount), 0) FROM RentalGroup rg
            WHERE rg.createdAt >= :since AND rg.status <> 'CANCELLED'
            """)
    BigDecimal sumRevenueSince(@Param("since") LocalDateTime since);

    @Query("""
            SELECT COUNT(rg) FROM RentalGroup rg
            WHERE rg.createdAt >= :since AND rg.status <> 'CANCELLED'
            """)
    long countTripsSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(rg) FROM RentalGroup rg WHERE rg.status = 'PENDING'")
    long countPendingBookings();

    /**
     * Dữ liệu biểu đồ theo ngày: trả về [date_str, sum_amount, count]
     * Dùng native SQL để PostgreSQL có thể GROUP BY DATE(created_at).
     */
    @Query(value = """
            SELECT TO_CHAR(created_at, 'YYYY-MM-DD') AS day,
                   COALESCE(SUM(total_amount), 0)    AS revenue,
                   COUNT(*)                           AS bookings
            FROM rental_group
            WHERE created_at >= :since
              AND status != 'CANCELLED'
            GROUP BY TO_CHAR(created_at, 'YYYY-MM-DD')
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> getDailyStats(@Param("since") LocalDateTime since);

    /** 10 booking mới nhất để hiển thị hoạt động gần đây */
    List<RentalGroup> findTop10ByOrderByCreatedAtDesc();
}
