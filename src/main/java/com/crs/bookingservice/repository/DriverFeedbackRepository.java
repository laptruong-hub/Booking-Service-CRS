package com.crs.bookingservice.repository;

import com.crs.bookingservice.entity.DriverFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DriverFeedbackRepository extends JpaRepository<DriverFeedback, Long> {

    List<DriverFeedback> findByDriverId(Long driverId);

    List<DriverFeedback> findByRentalUnitId(Long rentalUnitId);
}
