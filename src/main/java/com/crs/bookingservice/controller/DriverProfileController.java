package com.crs.bookingservice.controller;

import com.crs.bookingservice.client.IamServiceClient;
import com.crs.bookingservice.client.dto.IamUserDto;
import com.crs.bookingservice.dto.response.ApiResponse;
import com.crs.bookingservice.dto.response.DriverProfileResponse;
import com.crs.bookingservice.dto.response.PageResponse;
import com.crs.bookingservice.dto.response.RentalGroupResponse;
import com.crs.bookingservice.entity.DriverProfile;
import com.crs.bookingservice.enums.BookingStatus;
import com.crs.bookingservice.enums.DriverStatus;
import com.crs.bookingservice.exception.DuplicateResourceException;
import com.crs.bookingservice.exception.InvalidRequestException;
import com.crs.bookingservice.exception.ResourceNotFoundException;
import com.crs.bookingservice.repository.DriverProfileRepository;
import com.crs.bookingservice.service.RentalGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DriverProfileController — quản lý hồ sơ tài xế trong hệ thống.
 *
 * Tài xế được quản lý qua 2 lớp:
 * - IAM service: lưu thông tin user (tên, email, phone, role=DRIVER)
 * - Booking service (local): lưu thông tin nghề nghiệp (bằng lái, rating, trạng
 * thái)
 *
 * GET /api/v1/drivers → merge cả hai nguồn để hiển thị đầy đủ.
 */
@RestController
@RequestMapping("/api/v1/drivers")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Driver Profile", description = "API quản lý hồ sơ tài xế — dữ liệu từ IAM + booking-service")
public class DriverProfileController {

        private final DriverProfileRepository driverProfileRepository;
        private final IamServiceClient iamServiceClient;
        private final RentalGroupService rentalGroupService;

        // ================================================================
        // GET — Lấy danh sách tài xế từ IAM → enrich với local profile
        // ================================================================

        @GetMapping
        @Operation(summary = "Lấy danh sách tất cả tài xế có role DRIVER", description = """
                        Lấy tất cả users có `role=DRIVER` từ IAM service và enrich với
                        thông tin profile local (bằng lái, rating, trạng thái, vị trí).

                        - Users từ IAM nhưng **chưa có profile** sẽ vẫn xuất hiện (chưa có licenseNumber/rating).
                        - Dùng `POST /api/v1/drivers` để tạo profile cho từng tài xế.
                        """)
        public ResponseEntity<ApiResponse<List<DriverProfileResponse>>> getAllDrivers() {
                // 1. Lấy users có role DRIVER từ IAM
                List<IamUserDto> iamDrivers;
                try {
                        iamDrivers = iamServiceClient.getUsersByRole("DRIVER");
                        log.info("[Feign] Lấy được {} users có role DRIVER từ IAM.", iamDrivers.size());
                } catch (Exception e) {
                        log.warn("[Feign] Không thể kết nối IAM: {}. Fallback về local DriverProfile.", e.getMessage());
                        // Fallback: trả về local DriverProfile nếu IAM không available
                        var localDrivers = driverProfileRepository.findAll(PageRequest.of(0, 100)).getContent();
                        return ResponseEntity.ok(ApiResponse.success(
                                        localDrivers.stream().map(this::toResponse).toList(), "OK (local fallback)"));
                }

                // 2. Lấy tất cả local DriverProfile và index theo userId
                Map<String, DriverProfile> localProfileMap = driverProfileRepository.findAll()
                                .stream()
                                .collect(Collectors.toMap(DriverProfile::getUserId, p -> p, (a, b) -> a));

                // 3. Merge: với mỗi IAM user, lấy local profile nếu có
                List<DriverProfileResponse> result = iamDrivers.stream().map(iamUser -> {
                        DriverProfile local = localProfileMap.get(iamUser.getUserId());
                        return buildResponse(iamUser, local);
                }).toList();

                return ResponseEntity.ok(ApiResponse.success(result, "OK"));
        }

        @GetMapping("/{id}")
        @Operation(summary = "Lấy thông tin tài xế theo profile ID (local)")
        public ResponseEntity<ApiResponse<DriverProfileResponse>> getById(@PathVariable Long id) {
                DriverProfile driver = driverProfileRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("DriverProfile", id));

                // Enrich với IAM
                IamUserDto iamUser = null;
                try {
                        iamUser = iamServiceClient.getUserById(driver.getUserId());
                } catch (Exception e) {
                        log.warn("[Feign] Không lấy được IAM user cho driver #{}", id);
                }
                return ResponseEntity.ok(ApiResponse.success(buildResponse(iamUser, driver), "OK"));
        }

        @GetMapping("/by-user/{userId}")
        @Operation(summary = "Lấy thông tin tài xế theo userId (IAM UUID)", description = "Tìm kiếm driver profile bằng userId từ IAM service.")
        public ResponseEntity<ApiResponse<DriverProfileResponse>> getByUserId(@PathVariable String userId) {
                // Lấy IAM user
                IamUserDto iamUser;
                try {
                        iamUser = iamServiceClient.getUserById(userId);
                } catch (Exception e) {
                        throw new ResourceNotFoundException("User không tồn tại trong IAM: " + userId);
                }

                // Local profile (có thể chưa tồn tại)
                DriverProfile local = driverProfileRepository.findByUserId(userId).orElse(null);
                return ResponseEntity.ok(ApiResponse.success(buildResponse(iamUser, local), "OK"));
        }

        // ================================================================
        // POST — Tạo / đăng ký profile tài xế
        // ================================================================

        @PostMapping
        @Operation(summary = "Đăng ký hồ sơ nghề nghiệp cho tài xế", description = """
                        Tạo DriverProfile cho user đã có account **role=DRIVER** trong IAM service.

                        **Cần làm trước:**
                        1. Tạo user trong IAM với role DRIVER
                        2. Lấy `userId` từ `GET /api/v1/admin/users` (Swagger IAM port 8080)
                        3. Dùng `userId` đó để gọi endpoint này

                        Sau khi tạo, tài xế sẽ xuất hiện đầy đủ thông tin trong `GET /api/v1/drivers`.
                        """)
        public ResponseEntity<ApiResponse<DriverProfileResponse>> createDriver(
                        @RequestParam @NotBlank String userId,
                        @RequestParam @NotBlank String licenseNumber,
                        @RequestParam(required = false) String currentLocation) {

                // Validate: userId phải tồn tại trong IAM
                IamUserDto iamUser;
                try {
                        iamUser = iamServiceClient.getUserById(userId);
                } catch (Exception e) {
                        throw new InvalidRequestException("userId '" + userId + "' không tồn tại trong IAM service.");
                }

                // Validate: không trùng
                if (driverProfileRepository.existsByUserId(userId)) {
                        throw new DuplicateResourceException("userId " + userId + " đã có hồ sơ tài xế.");
                }
                if (driverProfileRepository.existsByLicenseNumber(licenseNumber)) {
                        throw new DuplicateResourceException("Số bằng lái " + licenseNumber + " đã được đăng ký.");
                }

                DriverProfile driver = DriverProfile.builder()
                                .userId(userId)
                                .licenseNumber(licenseNumber)
                                .status(DriverStatus.ACTIVE)
                                .currentLocation(currentLocation)
                                .build();

                driver = driverProfileRepository.save(driver);
                log.info("✅ Đã tạo DriverProfile cho user {} ({})", userId, iamUser.getFullName());

                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success(buildResponse(iamUser, driver),
                                                "Đã tạo hồ sơ tài xế thành công."));
        }

        // ================================================================
        // PUT — Cập nhật hồ sơ tài xế
        // ================================================================

        @PutMapping("/{id}")
        @Operation(summary = "Cập nhật hồ sơ nghề nghiệp cho tài xế", description = "Cập nhật thông tin bằng lái và vị trí của tài xế.")
        public ResponseEntity<ApiResponse<DriverProfileResponse>> updateDriver(
                        @PathVariable Long id,
                        @RequestParam @NotBlank String licenseNumber,
                        @RequestParam(required = false) String currentLocation) {

                DriverProfile driver = driverProfileRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("DriverProfile", id));

                // Validate: nếu đổi bằng lái mới thì check trùng
                if (!driver.getLicenseNumber().equals(licenseNumber)
                                && driverProfileRepository.existsByLicenseNumber(licenseNumber)) {
                        throw new DuplicateResourceException(
                                        "Số bằng lái " + licenseNumber + " đã được đăng ký cho tài xế khác.");
                }

                driver.setLicenseNumber(licenseNumber);
                driver.setCurrentLocation(currentLocation);
                driver = driverProfileRepository.save(driver);

                IamUserDto iamUser = null;
                try {
                        iamUser = iamServiceClient.getUserById(driver.getUserId());
                } catch (Exception ignored) {
                }

                return ResponseEntity.ok(ApiResponse.success(buildResponse(iamUser, driver),
                                "Đã cập nhật hồ sơ tài xế thành công."));
        }

        // ================================================================
        // PATCH — Cập nhật trạng thái
        // ================================================================

        @PatchMapping("/{id}/status")
        @Operation(summary = "Cập nhật trạng thái tài xế (ACTIVE / INACTIVE / BLOCKED)")
        public ResponseEntity<ApiResponse<DriverProfileResponse>> updateStatus(
                        @PathVariable Long id,
                        @RequestParam DriverStatus status) {
                DriverProfile driver = driverProfileRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("DriverProfile", id));
                driver.setStatus(status);
                driver = driverProfileRepository.save(driver);

                IamUserDto iamUser = null;
                try {
                        iamUser = iamServiceClient.getUserById(driver.getUserId());
                } catch (Exception ignored) {
                }

                return ResponseEntity.ok(ApiResponse.success(
                                buildResponse(iamUser, driver), "Đã cập nhật trạng thái tài xế."));
        }

        // ================================================================
        // GET — Lấy danh sách booking của tài xế
        // ================================================================

        @GetMapping("/{id}/bookings")
        @Operation(summary = "Lấy danh sách booking của tài xế theo profile ID", description = """
                        Trả về tất cả booking mà tài xế đã hoặc đang được phân công.
                        Có thể filter theo trạng thái booking (`status`).

                        **Các trạng thái booking:** `PENDING`, `CONFIRMED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`
                        """)
        public ResponseEntity<ApiResponse<PageResponse<RentalGroupResponse>>> getDriverBookings(
                        @PathVariable Long id,
                        @RequestParam(required = false) BookingStatus status,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size) {
                return ResponseEntity.ok(ApiResponse.success(
                                rentalGroupService.getBookingsByDriver(id, status, page, size),
                                "OK"));
        }

        // ================================================================
        // BUILDERS
        // ================================================================

        /**
         * Build response từ IAM user + local profile.
         * Một trong hai có thể null (IAM timeout hoặc chưa tạo profile).
         */
        private DriverProfileResponse buildResponse(IamUserDto iamUser, DriverProfile local) {
                return DriverProfileResponse.builder()
                                // Local profile info (null-safe)
                                .id(local != null ? local.getId() : null)
                                .userId(iamUser != null ? iamUser.getUserId()
                                                : (local != null ? local.getUserId() : null))
                                .licenseNumber(local != null ? local.getLicenseNumber() : null)
                                .status(local != null ? local.getStatus() : DriverStatus.ACTIVE)
                                .currentLocation(local != null ? local.getCurrentLocation() : null)
                                .averageRating(local != null ? local.getAverageRating() : null)
                                // IAM user info (null-safe)
                                .fullName(iamUser != null ? iamUser.getFullName() : null)
                                .email(iamUser != null ? iamUser.getEmail() : null)
                                .phone(iamUser != null ? iamUser.getPhone() : null)
                                .build();
        }

        private DriverProfileResponse toResponse(DriverProfile driver) {
                return buildResponse(null, driver);
        }
}
