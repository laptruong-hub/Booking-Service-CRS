package com.crs.bookingservice.repository;

import com.crs.bookingservice.entity.DriverProfile;
import com.crs.bookingservice.enums.DriverStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DriverProfileRepository extends JpaRepository<DriverProfile, Long> {

    Optional<DriverProfile> findByUserId(String userId);

    Optional<DriverProfile> findByLicenseNumber(String licenseNumber);

    boolean existsByUserId(String userId);

    boolean existsByLicenseNumber(String licenseNumber);

    Page<DriverProfile> findByStatus(DriverStatus status, Pageable pageable);
}
