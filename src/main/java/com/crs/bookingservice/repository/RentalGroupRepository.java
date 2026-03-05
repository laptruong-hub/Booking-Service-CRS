package com.crs.bookingservice.repository;

import com.crs.bookingservice.entity.RentalGroup;
import com.crs.bookingservice.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RentalGroupRepository extends JpaRepository<RentalGroup, Long> {

    Optional<RentalGroup> findByBookingCode(String bookingCode);

    boolean existsByBookingCode(String bookingCode);

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
}
