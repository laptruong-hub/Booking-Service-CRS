package com.crs.bookingservice.repository;

import com.crs.bookingservice.entity.RentalUnit;
import com.crs.bookingservice.enums.RentalUnitStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RentalUnitRepository extends JpaRepository<RentalUnit, Long> {

    List<RentalUnit> findByRentalGroupId(Long rentalGroupId);

    List<RentalUnit> findByVehicleId(Long vehicleId);

    List<RentalUnit> findByDriverId(Long driverId);

    List<RentalUnit> findByStatus(RentalUnitStatus status);

    boolean existsByVehicleIdAndStatusIn(Long vehicleId, List<RentalUnitStatus> statuses);

    boolean existsByDriverIdAndStatusIn(Long driverId, List<RentalUnitStatus> statuses);
}
