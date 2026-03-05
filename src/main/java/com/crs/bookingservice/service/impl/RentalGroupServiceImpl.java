package com.crs.bookingservice.service.impl;

import com.crs.bookingservice.client.CarManagementClient;
import com.crs.bookingservice.client.IamServiceClient;
import com.crs.bookingservice.client.dto.IamUserDto;
import com.crs.bookingservice.client.dto.VehicleDto;
import com.crs.bookingservice.dto.request.AssignDriverRequest;
import com.crs.bookingservice.dto.request.CreateRentalGroupRequest;
import com.crs.bookingservice.dto.request.CreateRentalUnitRequest;
import com.crs.bookingservice.dto.request.HandoverRequest;
import com.crs.bookingservice.dto.response.DriverProfileResponse;
import com.crs.bookingservice.dto.response.PageResponse;
import com.crs.bookingservice.dto.response.RentalGroupResponse;
import com.crs.bookingservice.dto.response.RentalUnitResponse;
import com.crs.bookingservice.entity.*;
import com.crs.bookingservice.enums.BookingStatus;
import com.crs.bookingservice.enums.DriverStatus;
import com.crs.bookingservice.enums.RentalUnitStatus;
import com.crs.bookingservice.exception.InvalidRequestException;
import com.crs.bookingservice.exception.ResourceNotFoundException;
import com.crs.bookingservice.repository.*;
import com.crs.bookingservice.service.RentalGroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class RentalGroupServiceImpl implements RentalGroupService {

    private final RentalGroupRepository rentalGroupRepository;
    private final RentalUnitRepository rentalUnitRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final HandoverProtocolRepository handoverProtocolRepository;
    private final IamServiceClient iamServiceClient;
    private final CarManagementClient carManagementClient;

    private final AtomicLong bookingCounter = new AtomicLong(1);

    // ================================================================
    // CUSTOMER OPERATIONS
    // ================================================================

    @Override
    @Transactional
    public RentalGroupResponse createBooking(CreateRentalGroupRequest request) {
        log.info("Tạo booking mới cho userId: {}", request.getUserId());

        // Validate địa chỉ giao xe
        if (request.getDeliveryMode() != null
                && request.getDeliveryMode().name().equals("DELIVERY")
                && (request.getDeliveryAddress() == null || request.getDeliveryAddress().isBlank())) {
            throw new InvalidRequestException("Địa chỉ giao xe là bắt buộc khi chọn giao tận nơi.");
        }

        // Validate và lấy thông tin xe từ car-management
        for (CreateRentalUnitRequest unit : request.getRentalUnits()) {
            if (unit.getStartTime() == null || unit.getEndTime() == null) {
                throw new InvalidRequestException("startTime và endTime là bắt buộc.");
            }
            if (!unit.getEndTime().isAfter(unit.getStartTime())) {
                throw new InvalidRequestException("endTime phải sau startTime cho vehicleId: " + unit.getVehicleId());
            }

            // Gọi car-management để validate xe tồn tại và đang AVAILABLE
            try {
                var vehicleResponse = carManagementClient.getVehicleById(unit.getVehicleId());
                if (vehicleResponse == null || vehicleResponse.getData() == null) {
                    throw new InvalidRequestException(
                            "Xe ID " + unit.getVehicleId() + " không tồn tại trong hệ thống.");
                }
                String vehicleStatus = vehicleResponse.getData().getStatus();
                if (!"AVAILABLE".equalsIgnoreCase(vehicleStatus)) {
                    throw new InvalidRequestException(
                            "Xe ID " + unit.getVehicleId() + " không khả dụng (trạng thái: " + vehicleStatus + ").");
                }
            } catch (InvalidRequestException e) {
                throw e; // Re-throw validation errors
            } catch (Exception e) {
                log.warn("[Feign] Không thể kết nối car-management để validate xe #{}: {}", unit.getVehicleId(),
                        e.getMessage());
                // Graceful fallback: nếu car-management down, chỉ check local conflict
            }

            boolean isConflict = rentalUnitRepository.existsByVehicleIdAndStatusIn(
                    unit.getVehicleId(),
                    List.of(RentalUnitStatus.PENDING, RentalUnitStatus.ACTIVE));
            if (isConflict) {
                throw new InvalidRequestException(
                        "Xe ID " + unit.getVehicleId() + " đang bận trong khoảng thời gian này.");
            }
        }

        // Tính tổng tiền
        BigDecimal totalAmount = request.getRentalUnits().stream()
                .map(u -> u.getUnitPrice() != null ? u.getUnitPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Tạo RentalGroup
        String bookingCode = generateBookingCode();
        RentalGroup rentalGroup = RentalGroup.builder()
                .userId(request.getUserId())
                .bookingCode(bookingCode)
                .deliveryMode(request.getDeliveryMode())
                .deliveryAddress(request.getDeliveryAddress())
                .totalAmount(totalAmount)
                .status(BookingStatus.PENDING)
                .build();

        rentalGroup = rentalGroupRepository.save(rentalGroup);
        final RentalGroup savedGroup = rentalGroup;

        // Tạo các RentalUnit
        for (CreateRentalUnitRequest unitReq : request.getRentalUnits()) {
            RentalUnit unit = RentalUnit.builder()
                    .rentalGroup(savedGroup)
                    .vehicleId(unitReq.getVehicleId())
                    .isWithDriver(Boolean.TRUE.equals(unitReq.getIsWithDriver()))
                    .startTime(unitReq.getStartTime())
                    .endTime(unitReq.getEndTime())
                    .unitPrice(unitReq.getUnitPrice())
                    .status(RentalUnitStatus.PENDING)
                    .build();

            rentalUnitRepository.save(unit);
        }

        log.info("✅ Đã tạo booking {} thành công → PENDING, chờ Staff xác nhận.", bookingCode);
        return toResponse(rentalGroupRepository.findById(savedGroup.getId()).orElseThrow());
    }

    @Override
    @Transactional
    public RentalGroupResponse cancelBooking(Long id, String reason) {
        RentalGroup group = getOrThrow(id);
        if (group.getStatus() == BookingStatus.COMPLETED || group.getStatus() == BookingStatus.CANCELLED) {
            throw new InvalidRequestException("Không thể huỷ booking đã hoàn tất hoặc đã bị huỷ.");
        }
        if (group.getStatus() == BookingStatus.IN_PROGRESS) {
            throw new InvalidRequestException("Không thể huỷ booking đang trong chuyến đi. Vui lòng liên hệ Staff.");
        }
        group.setStatus(BookingStatus.CANCELLED);
        group.getRentalUnits().forEach(u -> u.setStatus(RentalUnitStatus.CANCELLED));
        log.info("🚫 Booking {} đã bị huỷ. Lý do: {}", group.getBookingCode(), reason);
        return toResponse(rentalGroupRepository.save(group));
    }

    // ================================================================
    // QUERY OPERATIONS
    // ================================================================

    @Override
    @Transactional(readOnly = true)
    public RentalGroupResponse getBookingById(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public RentalGroupResponse getBookingByCode(String bookingCode) {
        return toResponse(rentalGroupRepository.findByBookingCode(bookingCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy booking với code: " + bookingCode)));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RentalGroupResponse> getBookingsByUser(String userId, int page, int size) {
        Page<RentalGroup> p = rentalGroupRepository.findByUserId(
                userId, PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return PageResponse.of(p.map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RentalGroupResponse> getAllBookings(BookingStatus status, int page, int size) {
        Page<RentalGroup> p = rentalGroupRepository.search(
                null, status, PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return PageResponse.of(p.map(this::toResponse));
    }

    // ================================================================
    // STAFF OPERATIONS
    // ================================================================

    /**
     * [STAFF] Gán tài xế cho xe trong booking.
     * Validate: tài xế phải ACTIVE và không có booking đang ACTIVE.
     * Chỉ áp dụng cho RentalUnit có isWithDriver = true.
     */
    @Override
    @Transactional
    public RentalGroupResponse assignDriver(Long bookingId, AssignDriverRequest request) {
        RentalGroup group = getOrThrow(bookingId);
        assertStatus(group, BookingStatus.PENDING, "Chỉ được gán tài xế cho booking đang PENDING.");

        RentalUnit unit = rentalUnitRepository.findById(request.getRentalUnitId())
                .orElseThrow(() -> new ResourceNotFoundException("RentalUnit", request.getRentalUnitId()));

        if (!unit.getRentalGroup().getId().equals(bookingId)) {
            throw new InvalidRequestException("RentalUnit này không thuộc booking #" + bookingId);
        }
        if (!Boolean.TRUE.equals(unit.getIsWithDriver())) {
            throw new InvalidRequestException("RentalUnit này không yêu cầu tài xế (isWithDriver = false).");
        }

        DriverProfile driver = driverProfileRepository.findById(request.getDriverId())
                .orElseThrow(() -> new ResourceNotFoundException("DriverProfile", request.getDriverId()));

        if (driver.getStatus() != DriverStatus.ACTIVE) {
            throw new InvalidRequestException("Tài xế này không đang hoạt động (status: " + driver.getStatus() + ").");
        }

        // Kiểm tra tài xế có đang bận booking nào không
        boolean driverBusy = rentalUnitRepository.existsByDriverIdAndStatusIn(
                driver.getId(), List.of(RentalUnitStatus.PENDING, RentalUnitStatus.ACTIVE));
        if (driverBusy) {
            throw new InvalidRequestException("Tài xế này đang bận với chuyến khác. Vui lòng chọn tài xế khác.");
        }

        unit.setDriver(driver);
        rentalUnitRepository.save(unit);

        log.info("👨‍✈️ Đã gán tài xế #{} cho RentalUnit #{} trong booking {}.",
                driver.getId(), unit.getId(), group.getBookingCode());
        return toResponse(getOrThrow(bookingId));
    }

    /**
     * [STAFF] Xác nhận booking → CONFIRMED.
     * Validate: nếu booking có xe cần tài xế, tất cả xe đó phải đã được gán tài xế.
     */
    @Override
    @Transactional
    public RentalGroupResponse confirmBooking(Long id) {
        RentalGroup group = getOrThrow(id);
        assertStatus(group, BookingStatus.PENDING, "Chỉ có thể xác nhận booking đang PENDING.");

        // Validate: xe nào cần tài xế phải đã được gán
        boolean hasMissingDriver = group.getRentalUnits().stream()
                .anyMatch(u -> Boolean.TRUE.equals(u.getIsWithDriver()) && u.getDriver() == null);
        if (hasMissingDriver) {
            throw new InvalidRequestException(
                    "Booking có xe yêu cầu tài xế nhưng chưa gán đủ. Vui lòng gán tài xế trước khi xác nhận.");
        }

        group.setStatus(BookingStatus.CONFIRMED);
        log.info("✅ Staff đã xác nhận booking {} → CONFIRMED.", group.getBookingCode());

        // Log thông báo khách
        boolean hasDriverTrip = group.getRentalUnits().stream()
                .anyMatch(u -> Boolean.TRUE.equals(u.getIsWithDriver()));
        if (hasDriverTrip) {
            log.info("🚗 Tài xế sẽ đến FleetHub lấy xe rồi rước khách tại điểm đón.");
        } else {
            log.info("📍 Yêu cầu khách đến bãi xe gần nhất để nhận xe.");
        }

        return toResponse(rentalGroupRepository.save(group));
    }

    /**
     * [STAFF] Bàn giao xe cho khách tại bãi (booking không có tài xế).
     * Tạo HandoverProtocol PICKUP → RentalUnit chuyển sang ACTIVE → booking
     * IN_PROGRESS.
     */
    @Override
    @Transactional
    public RentalGroupResponse staffHandoverStart(Long bookingId, HandoverRequest request) {
        RentalGroup group = getOrThrow(bookingId);
        assertStatus(group, BookingStatus.CONFIRMED, "Chỉ có thể bàn giao xe khi booking đã CONFIRMED.");

        RentalUnit unit = getUnitOfBooking(bookingId, request.getRentalUnitId());

        if (Boolean.TRUE.equals(unit.getIsWithDriver())) {
            throw new InvalidRequestException(
                    "Xe này có tài xế. Tài xế phải dùng endpoint /driver-pickup-confirmed để bắt đầu chuyến.");
        }

        createHandoverProtocol(unit, "PICKUP", request);
        unit.setStatus(RentalUnitStatus.ACTIVE);
        rentalUnitRepository.save(unit);

        // Nếu tất cả unit đã ACTIVE → booking IN_PROGRESS
        transitionToInProgressIfAllActive(group);

        log.info("🤝 Staff đã bàn giao xe cho khách. RentalUnit #{} → ACTIVE.", unit.getId());
        return toResponse(getOrThrow(bookingId));
    }

    /**
     * [STAFF] Nhận xe lại từ khách, kết thúc booking.
     * Tạo HandoverProtocol RETURN → RentalUnit chuyển sang RETURNED → booking
     * COMPLETED.
     */
    @Override
    @Transactional
    public RentalGroupResponse staffHandoverReturn(Long bookingId, HandoverRequest request) {
        RentalGroup group = getOrThrow(bookingId);
        assertStatus(group, BookingStatus.IN_PROGRESS, "Chỉ có thể nhận xe lại khi booking đang IN_PROGRESS.");

        RentalUnit unit = getUnitOfBooking(bookingId, request.getRentalUnitId());
        if (unit.getStatus() != RentalUnitStatus.ACTIVE) {
            throw new InvalidRequestException("RentalUnit này chưa ở trạng thái ACTIVE.");
        }

        createHandoverProtocol(unit, "RETURN", request);
        unit.setStatus(RentalUnitStatus.RETURNED);
        unit.setActualReturnTime(LocalDateTime.now());
        rentalUnitRepository.save(unit);

        // Nếu tất cả unit đã RETURNED → booking COMPLETED
        transitionToCompletedIfAllReturned(group);

        log.info("✅ Staff đã nhận xe lại. RentalUnit #{} → RETURNED.", unit.getId());
        return toResponse(getOrThrow(bookingId));
    }

    // ================================================================
    // DRIVER OPERATIONS
    // ================================================================

    /**
     * [DRIVER] Xác nhận đã đón được khách tại điểm đón → bắt đầu chuyến đi.
     * Tạo HandoverProtocol PICKUP → RentalUnit chuyển sang ACTIVE → booking
     * IN_PROGRESS.
     */
    @Override
    @Transactional
    public RentalGroupResponse driverConfirmPickup(Long bookingId, HandoverRequest request) {
        RentalGroup group = getOrThrow(bookingId);
        assertStatus(group, BookingStatus.CONFIRMED, "Booking phải ở trạng thái CONFIRMED để bắt đầu chuyến.");

        RentalUnit unit = getUnitOfBooking(bookingId, request.getRentalUnitId());

        if (!Boolean.TRUE.equals(unit.getIsWithDriver())) {
            throw new InvalidRequestException("Xe này không có tài xế. Dùng endpoint staff-handover-start.");
        }
        if (unit.getDriver() == null) {
            throw new InvalidRequestException("Chưa gán tài xế cho xe này. Vui lòng liên hệ Staff.");
        }

        createHandoverProtocol(unit, "PICKUP", request);
        unit.setStatus(RentalUnitStatus.ACTIVE);
        rentalUnitRepository.save(unit);

        transitionToInProgressIfAllActive(group);

        log.info("🚀 Tài xế đã đón khách. RentalUnit #{} → ACTIVE. Chuyến đi bắt đầu!", unit.getId());
        return toResponse(getOrThrow(bookingId));
    }

    /**
     * [DRIVER] Hoàn thành chuyến đi: tài xế đã đưa khách đến điểm đến và trả xe về
     * Hub.
     * Tạo HandoverProtocol RETURN → RentalUnit chuyển sang RETURNED → booking
     * COMPLETED.
     */
    @Override
    @Transactional
    public RentalGroupResponse driverCompleteTrip(Long bookingId, HandoverRequest request) {
        RentalGroup group = getOrThrow(bookingId);
        assertStatus(group, BookingStatus.IN_PROGRESS, "Booking phải đang IN_PROGRESS để hoàn thành.");

        RentalUnit unit = getUnitOfBooking(bookingId, request.getRentalUnitId());
        if (unit.getStatus() != RentalUnitStatus.ACTIVE) {
            throw new InvalidRequestException("RentalUnit không ở trạng thái ACTIVE.");
        }

        createHandoverProtocol(unit, "RETURN", request);
        unit.setStatus(RentalUnitStatus.RETURNED);
        unit.setActualReturnTime(LocalDateTime.now());
        rentalUnitRepository.save(unit);

        transitionToCompletedIfAllReturned(group);

        log.info("🏁 Tài xế hoàn thành chuyến. RentalUnit #{} → RETURNED.", unit.getId());
        return toResponse(getOrThrow(bookingId));
    }

    // ================================================================
    // DRIVER PROFILE QUERIES
    // ================================================================

    /**
     * Lấy danh sách tài xế ACTIVE từ IAM service, merge với DriverProfile local để
     * lọc busy drivers
     */
    @Override
    @Transactional(readOnly = true)
    public List<DriverProfileResponse> getAvailableDrivers() {
        // 1. Lấy danh sách DriverProfile local đang ACTIVE
        List<DriverProfile> localActiveDrivers = driverProfileRepository
                .findByStatus(DriverStatus.ACTIVE, PageRequest.of(0, 200)).getContent();

        // 2. Lọc bỏ driver đang có booking PENDING/ACTIVE
        List<DriverProfile> freeDrivers = localActiveDrivers.stream()
                .filter(driver -> !rentalUnitRepository.existsByDriverIdAndStatusIn(
                        driver.getId(), List.of(RentalUnitStatus.PENDING, RentalUnitStatus.ACTIVE)))
                .toList();

        // 3. Enrich với thông tin từ IAM service
        return freeDrivers.stream().map(driver -> {
            DriverProfileResponse response = toDriverResponse(driver);
            try {
                IamUserDto iamUser = iamServiceClient.getUserById(driver.getUserId());
                if (iamUser != null) {
                    response.setFullName(iamUser.getFullName());
                    response.setEmail(iamUser.getEmail());
                    response.setPhone(iamUser.getPhone());
                }
            } catch (Exception e) {
                log.warn("[Feign] Không thể lấy thông tin IAM cho driver #{}: {}", driver.getId(), e.getMessage());
            }
            return response;
        }).toList();
    }

    // ================================================================
    // PRIVATE HELPERS
    // ================================================================

    private RentalGroup getOrThrow(Long id) {
        return rentalGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", id));
    }

    private RentalUnit getUnitOfBooking(Long bookingId, Long unitId) {
        RentalUnit unit = rentalUnitRepository.findById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException("RentalUnit", unitId));
        if (!unit.getRentalGroup().getId().equals(bookingId)) {
            throw new InvalidRequestException("RentalUnit #" + unitId + " không thuộc booking #" + bookingId);
        }
        return unit;
    }

    private void assertStatus(RentalGroup group, BookingStatus expected, String message) {
        if (group.getStatus() != expected) {
            throw new InvalidRequestException(message + " Trạng thái hiện tại: " + group.getStatus());
        }
    }

    private void createHandoverProtocol(RentalUnit unit, String type, HandoverRequest req) {
        // Xoá handover cũ cùng type nếu có (tránh duplicate)
        handoverProtocolRepository.findByRentalUnitIdAndType(unit.getId(), type)
                .ifPresent(handoverProtocolRepository::delete);

        HandoverProtocol protocol = HandoverProtocol.builder()
                .rentalUnit(unit)
                .type(type)
                .odoMeter(req.getOdoMeter())
                .condition(req.getCondition())
                .photos(req.getPhotos())
                .build();
        handoverProtocolRepository.save(protocol);
        log.info("📝 Tạo HandoverProtocol type={} cho RentalUnit #{}.", type, unit.getId());
    }

    private void transitionToInProgressIfAllActive(RentalGroup group) {
        List<RentalUnit> allUnits = rentalUnitRepository.findByRentalGroupId(group.getId());
        boolean allActive = allUnits.stream()
                .filter(u -> u.getStatus() != RentalUnitStatus.CANCELLED)
                .allMatch(u -> u.getStatus() == RentalUnitStatus.ACTIVE);
        if (allActive) {
            group.setStatus(BookingStatus.IN_PROGRESS);
            rentalGroupRepository.save(group);
            log.info("🚦 Tất cả xe đã ACTIVE → Booking {} chuyển sang IN_PROGRESS.", group.getBookingCode());
        }
    }

    private void transitionToCompletedIfAllReturned(RentalGroup group) {
        List<RentalUnit> allUnits = rentalUnitRepository.findByRentalGroupId(group.getId());
        boolean allReturned = allUnits.stream()
                .filter(u -> u.getStatus() != RentalUnitStatus.CANCELLED)
                .allMatch(u -> u.getStatus() == RentalUnitStatus.RETURNED);
        if (allReturned) {
            group.setStatus(BookingStatus.COMPLETED);
            rentalGroupRepository.save(group);
            log.info("🏆 Tất cả xe đã RETURNED → Booking {} → COMPLETED.", group.getBookingCode());
        }
    }

    private String generateBookingCode() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return String.format("BK-%s-%03d", date, bookingCounter.getAndIncrement());
    }

    private RentalGroupResponse toResponse(RentalGroup group) {
        List<RentalUnitResponse> units = group.getRentalUnits().stream()
                .map(this::toUnitResponse).toList();

        RentalGroupResponse response = RentalGroupResponse.builder()
                .id(group.getId())
                .userId(group.getUserId())
                .bookingCode(group.getBookingCode())
                .deliveryMode(group.getDeliveryMode())
                .deliveryAddress(group.getDeliveryAddress())
                .deliveryFee(group.getDeliveryFee())
                .totalAmount(group.getTotalAmount())
                .depositRequired(group.getDepositRequired())
                .status(group.getStatus())
                .createdAt(group.getCreatedAt())
                .rentalUnits(units)
                .build();

        // Enrich customer info từ iam-service
        try {
            IamUserDto customer = iamServiceClient.getUserById(group.getUserId());
            if (customer != null) {
                response.setCustomerName(customer.getFullName());
                response.setCustomerEmail(customer.getEmail());
                response.setCustomerPhone(customer.getPhone());
            }
        } catch (Exception e) {
            log.warn("[Feign] Không thể lấy thông tin khách hàng từ IAM cho userId {}: {}", group.getUserId(),
                    e.getMessage());
        }

        return response;
    }

    private RentalUnitResponse toUnitResponse(RentalUnit unit) {
        RentalUnitResponse response = RentalUnitResponse.builder()
                .id(unit.getId())
                .vehicleId(unit.getVehicleId())
                .driverId(unit.getDriver() != null ? unit.getDriver().getId() : null)
                .isWithDriver(unit.getIsWithDriver())
                .startTime(unit.getStartTime())
                .endTime(unit.getEndTime())
                .actualReturnTime(unit.getActualReturnTime())
                .unitPrice(unit.getUnitPrice())
                .faultPercent(unit.getFaultPercent())
                .status(unit.getStatus())
                .build();

        // Enrich vehicle info từ car-management
        try {
            var vehicleResp = carManagementClient.getVehicleById(unit.getVehicleId());
            if (vehicleResp != null && vehicleResp.getData() != null) {
                VehicleDto v = vehicleResp.getData();
                response.setVehiclePlateNumber(v.getPlateNumber());
                response.setVehicleBrand(v.getBrand());
                response.setVehicleModel(v.getModelName());
                response.setVehicleStatus(v.getStatus());
            }
        } catch (Exception e) {
            log.warn("[Feign] Không thể lấy thông tin xe #{} từ car-management: {}", unit.getVehicleId(),
                    e.getMessage());
        }

        // Enrich driver info từ iam-service
        if (unit.getDriver() != null) {
            try {
                IamUserDto driverUser = iamServiceClient.getUserById(unit.getDriver().getUserId());
                if (driverUser != null) {
                    response.setDriverName(driverUser.getFullName());
                    response.setDriverPhone(driverUser.getPhone());
                }
            } catch (Exception e) {
                log.warn("[Feign] Không thể lấy thông tin tài xế #{} từ IAM: {}", unit.getDriver().getUserId(),
                        e.getMessage());
            }
        }

        return response;
    }

    private DriverProfileResponse toDriverResponse(DriverProfile driver) {
        return DriverProfileResponse.builder()
                .id(driver.getId())
                .userId(driver.getUserId())
                .licenseNumber(driver.getLicenseNumber())
                .status(driver.getStatus())
                .currentLocation(driver.getCurrentLocation())
                .averageRating(driver.getAverageRating())
                .build();
    }
}
