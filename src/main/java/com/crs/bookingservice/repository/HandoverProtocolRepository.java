package com.crs.bookingservice.repository;

import com.crs.bookingservice.entity.HandoverProtocol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HandoverProtocolRepository extends JpaRepository<HandoverProtocol, Long> {

    Optional<HandoverProtocol> findByRentalUnitId(Long rentalUnitId);

    Optional<HandoverProtocol> findByRentalUnitIdAndType(Long rentalUnitId, String type);
}
