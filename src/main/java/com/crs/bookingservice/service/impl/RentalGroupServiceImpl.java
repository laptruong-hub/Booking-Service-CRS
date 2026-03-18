package com.crs.bookingservice.service.impl;

import com.crs.bookingservice.client.CarManagementClient;
import com.crs.bookingservice.client.IamServiceClient;
import com.crs.bookingservice.client.dto.IamUserDto;
import com.crs.bookingservice.client.dto.VehicleDto;
import com.crs.bookingservice.dto.request.AssignDriverRequest;
import com.crs.bookingservice.dto.request.CreateRentalGroupRequest;
import com.crs.bookingservice.dto.request.CreateRentalUnitRequest;
import com.crs.bookingservice.dto.request.HandoverRequest;
import com.crs.bookingservice.dto.request.StaffHandoverScanRequest;
import com.crs.bookingservice.dto.response.DriverProfileResponse;
import com.crs.bookingservice.dto.response.HandoverAiPreviewResponse;
import com.crs.bookingservice.dto.response.PageResponse;
import com.crs.bookingservice.dto.response.RentalGroupResponse;
import com.crs.bookingservice.dto.response.RentalUnitResponse;
import com.crs.bookingservice.entity.*;
import com.crs.bookingservice.enums.BookingStatus;
import com.crs.bookingservice.enums.DriverStatus;
import com.crs.bookingservice.enums.FeeApprovalStatus;
import com.crs.bookingservice.enums.InvoiceType;
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
import com.crs.bookingservice.dto.request.HandoverVehiclePhotoRequest;
import com.crs.bookingservice.service.VehicleInspectionAiService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

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
    private final VehicleInspectionAiService vehicleInspectionAiService;
    private final ObjectMapper objectMapper;
    private final VehicleInspectionAnalysisRepository vehicleInspectionAnalysisRepository;
    private final IncurredFeeRepository incurredFeeRepository;
    private final InvoiceRepository invoiceRepository;
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

        HandoverProtocol protocol = createHandoverProtocol(unit, "PICKUP", request);
        bindSelectedInspectionToProtocol(bookingId, unit.getId(), "PICKUP", request.getInspectionAnalysisId(), protocol);
        unit.setStatus(RentalUnitStatus.ACTIVE);
        rentalUnitRepository.save(unit);

        // Nếu tất cả unit đã ACTIVE → booking IN_PROGRESS
        transitionToInProgressIfAllActive(group);

        log.info("🤝 Staff đã bàn giao xe cho khách. RentalUnit #{} → ACTIVE.", unit.getId());
        return toResponse(getOrThrow(bookingId));
    }

    @Override
    @Transactional
    public HandoverAiPreviewResponse previewStaffHandoverStart(Long bookingId, StaffHandoverScanRequest request) {
        RentalGroup group = getOrThrow(bookingId);
        assertStatus(group, BookingStatus.CONFIRMED, "Chỉ có thể preview bàn giao khi booking đã CONFIRMED.");

        RentalUnit unit = getUnitOfBooking(bookingId, request.getRentalUnitId());
        if (Boolean.TRUE.equals(unit.getIsWithDriver())) {
            throw new InvalidRequestException(
                    "Xe này có tài xế. Tài xế phải dùng endpoint /driver-pickup-confirmed để bắt đầu chuyến.");
        }

            validateVehiclePhotosForAiScan(request.getVehiclePhotos());

            return vehicleInspectionAiService.scanHandover(
                bookingId,
                unit.getId(),
                "PICKUP",
                request.getVehiclePhotos());
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

        HandoverProtocol protocol = createHandoverProtocol(unit, "RETURN", request);
        bindSelectedInspectionToProtocol(bookingId, unit.getId(), "RETURN", request.getInspectionAnalysisId(), protocol);
        applyApprovedIncurredFee(group, unit, request);
        unit.setStatus(RentalUnitStatus.RETURNED);
        unit.setActualReturnTime(LocalDateTime.now());
        rentalUnitRepository.save(unit);

        // Nếu tất cả unit đã RETURNED → booking COMPLETED
        transitionToCompletedIfAllReturned(group);

        log.info("✅ Staff đã nhận xe lại. RentalUnit #{} → RETURNED.", unit.getId());
        return toResponse(getOrThrow(bookingId));
    }

    @Override
    @Transactional
    public HandoverAiPreviewResponse previewStaffHandoverReturn(Long bookingId, StaffHandoverScanRequest request) {
        RentalGroup group = getOrThrow(bookingId);
        assertStatus(group, BookingStatus.IN_PROGRESS, "Chỉ có thể preview nhận xe khi booking đang IN_PROGRESS.");

        RentalUnit unit = getUnitOfBooking(bookingId, request.getRentalUnitId());
        if (unit.getStatus() != RentalUnitStatus.ACTIVE) {
            throw new InvalidRequestException("RentalUnit này chưa ở trạng thái ACTIVE.");
        }

        validateVehiclePhotosForAiScan(request.getVehiclePhotos());

        return vehicleInspectionAiService.scanHandover(
                bookingId,
                unit.getId(),
                "RETURN",
                request.getVehiclePhotos());
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

    private HandoverProtocol createHandoverProtocol(RentalUnit unit, String type, HandoverRequest req) {
        // Xoá handover cũ cùng type nếu có (tránh duplicate)
        handoverProtocolRepository.findByRentalUnitIdAndType(unit.getId(), type)
                .ifPresent(handoverProtocolRepository::delete);

        HandoverProtocol protocol = HandoverProtocol.builder()
                .rentalUnit(unit)
                .type(type)
                .odoMeter(req.getOdoMeter())
                .condition(req.getCondition())
                .photos("[]")
                .build();
        HandoverProtocol saved = handoverProtocolRepository.save(protocol);
        log.info("📝 Tạo HandoverProtocol type={} cho RentalUnit #{}.", type, unit.getId());
        return saved;
    }

    private void bindSelectedInspectionToProtocol(
            Long bookingId,
            Long rentalUnitId,
            String stage,
            Long inspectionAnalysisId,
            HandoverProtocol protocol
    ) {
        if (inspectionAnalysisId == null) {
            return;
        }

        VehicleInspectionAnalysis analysis = vehicleInspectionAnalysisRepository.findById(inspectionAnalysisId)
                .orElseThrow(() -> new InvalidRequestException(
                        "inspectionAnalysisId không tồn tại: " + inspectionAnalysisId));

        if (!bookingId.equals(analysis.getBookingId())) {
            throw new InvalidRequestException("inspectionAnalysisId không thuộc booking hiện tại.");
        }

        if (!rentalUnitId.equals(analysis.getRentalUnitId())) {
            throw new InvalidRequestException("inspectionAnalysisId không thuộc rentalUnit hiện tại.");
        }

        if (!stage.equalsIgnoreCase(analysis.getStage())) {
            throw new InvalidRequestException(
                    "inspectionAnalysisId không đúng stage " + stage + ".");
        }

        Long existingProtocolId = analysis.getHandoverProtocolId();
        if (existingProtocolId != null && !existingProtocolId.equals(protocol.getId())) {
            throw new InvalidRequestException("inspectionAnalysisId đã được bind với biên bản khác.");
        }

        analysis.setHandoverProtocolId(protocol.getId());
        vehicleInspectionAnalysisRepository.save(analysis);
    }

    private void applyApprovedIncurredFee(RentalGroup group, RentalUnit unit, HandoverRequest request) {
        BigDecimal finalFee = resolveFinalIncurredFee(request);
        if (finalFee.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        IncurredFee feeRecord;
        Long analysisId = request.getInspectionAnalysisId();
        if (analysisId != null) {
            Optional<IncurredFee> existing = incurredFeeRepository.findByAiAnalysisId(analysisId);
            if (existing.isPresent()) {
                feeRecord = existing.get();
                if (!unit.getId().equals(feeRecord.getRentalUnit().getId())) {
                    throw new InvalidRequestException("IncurredFee theo inspectionAnalysisId không thuộc rentalUnit hiện tại.");
                }
                if (feeRecord.getApprovalStatus() == FeeApprovalStatus.APPROVED) {
                    return;
                }
                feeRecord.setTotalFee(finalFee);
                feeRecord.setApprovalStatus(FeeApprovalStatus.APPROVED);
                feeRecord.setDescription(buildApprovedFeeDescription(feeRecord.getDescription()));
            } else {
                feeRecord = IncurredFee.builder()
                        .rentalUnit(unit)
                        .surchargePolicy(null)
                        .totalFee(finalFee)
                        .description("[STAFF_APPROVED_AT_RETURN] Direct approved from return confirmation")
                        .aiSuggested(true)
                        .approvalStatus(FeeApprovalStatus.APPROVED)
                        .aiAnalysisId(analysisId)
                        .build();
            }
        } else {
            feeRecord = IncurredFee.builder()
                    .rentalUnit(unit)
                    .surchargePolicy(null)
                    .totalFee(finalFee)
                    .description("[STAFF_APPROVED_AT_RETURN] Manual approved at return confirmation")
                    .aiSuggested(false)
                    .approvalStatus(FeeApprovalStatus.APPROVED)
                    .aiAnalysisId(null)
                    .build();
        }

        incurredFeeRepository.save(feeRecord);

        BigDecimal currentTotal = group.getTotalAmount() == null ? BigDecimal.ZERO : group.getTotalAmount();
        group.setTotalAmount(currentTotal.add(finalFee));
        rentalGroupRepository.save(group);

        Invoice invoice = Invoice.builder()
                .rentalGroup(group)
                .amount(finalFee)
                .type(InvoiceType.INCURRED)
                .paymentMethod(null)
                .build();
        invoiceRepository.save(invoice);
    }

    private BigDecimal resolveFinalIncurredFee(HandoverRequest request) {
        BigDecimal fee = request.getFinalIncurredFee();
        if (fee == null) {
            return BigDecimal.ZERO;
        }
        if (fee.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidRequestException("finalIncurredFee không được âm.");
        }
        return fee;
    }

    private String buildApprovedFeeDescription(String existingDescription) {
        String prefix = "[STAFF_APPROVED_AT_RETURN]";
        if (existingDescription == null || existingDescription.isBlank()) {
            return prefix + " Approved at return confirmation";
        }
        if (existingDescription.startsWith(prefix)) {
            return existingDescription;
        }
        return prefix + " " + existingDescription;
    }

    private void validateVehiclePhotosForAiScan(List<HandoverVehiclePhotoRequest> vehiclePhotos) {
        if (vehiclePhotos == null || vehiclePhotos.size() != 4) {
            throw new InvalidRequestException("Scan AI yêu cầu đúng 4 ảnh xe.");
        }

        Set<com.crs.bookingservice.enums.VehiclePhotoCorner> corners = vehiclePhotos.stream()
                .map(HandoverVehiclePhotoRequest::getCorner)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (!corners.equals(EnumSet.allOf(com.crs.bookingservice.enums.VehiclePhotoCorner.class))) {
            throw new InvalidRequestException(
                    "Scan AI phải có đủ 4 góc: FRONT_LEFT, FRONT_RIGHT, REAR_LEFT, REAR_RIGHT và không trùng.");
        }
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
        // Enrich AI inspection info (prefer analysis bound to handover protocol)
        try {
            resolvePreferredInspectionAnalysis(unit).ifPresent(analysis -> {
                response.setInspectionAnalysisId(analysis.getId());
                response.setInspectionStage(analysis.getStage());
                response.setInspectionStatus(analysis.getAnalysisStatus());
                response.setInspectionSeverity(analysis.getSeverity());
                response.setInspectionRecommendedFee(analysis.getRecommendedFee());
                response.setNeedsManualReview(analysis.isNeedsManualReview());
                response.setComparisonSummary(extractComparisonSummary(analysis));
                response.setNewDamageDetected(extractNewDamageDetected(analysis));
            });
        } catch (Exception e) {
            log.warn("[AI] Không thể enrich inspection cho RentalUnit #{}: {}", unit.getId(), e.getMessage());
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

    private Optional<VehicleInspectionAnalysis> resolvePreferredInspectionAnalysis(RentalUnit unit) {
        Optional<HandoverProtocol> returnProtocol =
                handoverProtocolRepository.findByRentalUnitIdAndType(unit.getId(), "RETURN");
        if (returnProtocol.isPresent()) {
            Optional<VehicleInspectionAnalysis> boundReturnAnalysis = vehicleInspectionAnalysisRepository
                    .findFirstByHandoverProtocolIdOrderByCreatedAtDesc(returnProtocol.get().getId());
            if (boundReturnAnalysis.isPresent()) {
                return boundReturnAnalysis;
            }
        }

        Optional<HandoverProtocol> pickupProtocol =
                handoverProtocolRepository.findByRentalUnitIdAndType(unit.getId(), "PICKUP");
        if (pickupProtocol.isPresent()) {
            Optional<VehicleInspectionAnalysis> boundPickupAnalysis = vehicleInspectionAnalysisRepository
                    .findFirstByHandoverProtocolIdOrderByCreatedAtDesc(pickupProtocol.get().getId());
            if (boundPickupAnalysis.isPresent()) {
                return boundPickupAnalysis;
            }
        }

        return vehicleInspectionAnalysisRepository.findFirstByRentalUnitIdOrderByCreatedAtDesc(unit.getId());
    }

    private String extractComparisonSummary(VehicleInspectionAnalysis analysis) {
        if (analysis.getComparisonResultJson() != null) {
            try {
                com.fasterxml.jackson.databind.JsonNode node =
                        objectMapper.readTree(analysis.getComparisonResultJson());
                String s = node.path("summary").asText(null);
                if (s != null && !s.isBlank()) return s;
            } catch (Exception ignored) {}
        }
        return analysis.getSummary();
    }

    private Boolean extractNewDamageDetected(VehicleInspectionAnalysis analysis) {
        if (analysis.getComparisonResultJson() == null) return null;
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    objectMapper.readTree(analysis.getComparisonResultJson());
            if (node.has("newDamageDetected")) {
                return node.path("newDamageDetected").asBoolean(false);
            }
        } catch (Exception ignored) {}
        return null;
    }
}
