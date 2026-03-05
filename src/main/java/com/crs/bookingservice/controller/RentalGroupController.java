package com.crs.bookingservice.controller;

import com.crs.bookingservice.dto.request.AssignDriverRequest;
import com.crs.bookingservice.dto.request.CreateRentalGroupRequest;
import com.crs.bookingservice.dto.request.HandoverRequest;
import com.crs.bookingservice.dto.response.ApiResponse;
import com.crs.bookingservice.dto.response.DriverProfileResponse;
import com.crs.bookingservice.dto.response.PageResponse;
import com.crs.bookingservice.dto.response.RentalGroupResponse;
import com.crs.bookingservice.enums.BookingStatus;
import com.crs.bookingservice.service.RentalGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Booking Workflow", description = "API quản lý đặt xe — đầy đủ workflow")
public class RentalGroupController {

        private final RentalGroupService rentalGroupService;

        // ================================================================
        // CUSTOMER ENDPOINTS
        // ================================================================

        @PostMapping
        @Operation(summary = "[CUSTOMER] Tạo booking mới", description = """
                        Khách hàng tạo đơn đặt xe sau khi chọn xe, điểm đón và điểm trả.
                        - Booking sẽ ở trạng thái **PENDING**, chờ Staff xác nhận.
                        - `isWithDriver=true`: yêu cầu tài xế đi kèm. Staff sẽ gán tài xế trước khi confirm.
                        - `isWithDriver=false`: khách tự lái, đến bãi nhận xe.
                        """)
        public ResponseEntity<ApiResponse<RentalGroupResponse>> createBooking(
                        @Valid @RequestBody CreateRentalGroupRequest request) {
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success(rentalGroupService.createBooking(request),
                                                "Đơn đặt xe đã được tạo, chờ Staff xác nhận."));
        }

        @PatchMapping("/{id}/cancel")
        @Operation(summary = "[CUSTOMER/STAFF] Huỷ booking", description = "Huỷ booking khi chưa IN_PROGRESS. Có thể kèm lý do huỷ.")
        public ResponseEntity<ApiResponse<RentalGroupResponse>> cancelBooking(
                        @PathVariable Long id,
                        @RequestParam(defaultValue = "") String reason) {
                return ResponseEntity.ok(ApiResponse.success(
                                rentalGroupService.cancelBooking(id, reason), "Booking đã được huỷ."));
        }

        // ================================================================
        // QUERY ENDPOINTS
        // ================================================================

        @GetMapping("/{id}")
        @Operation(summary = "Lấy chi tiết booking theo ID")
        public ResponseEntity<ApiResponse<RentalGroupResponse>> getById(@PathVariable Long id) {
                return ResponseEntity.ok(ApiResponse.success(rentalGroupService.getBookingById(id), "OK"));
        }

        @GetMapping("/code/{bookingCode}")
        @Operation(summary = "Lấy chi tiết booking theo mã")
        public ResponseEntity<ApiResponse<RentalGroupResponse>> getByCode(@PathVariable String bookingCode) {
                return ResponseEntity.ok(ApiResponse.success(rentalGroupService.getBookingByCode(bookingCode), "OK"));
        }

        @GetMapping
        @Operation(summary = "Danh sách tất cả booking (phân trang, filter theo status)")
        public ResponseEntity<ApiResponse<PageResponse<RentalGroupResponse>>> getAll(
                        @RequestParam(required = false) BookingStatus status,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size) {
                return ResponseEntity
                                .ok(ApiResponse.success(rentalGroupService.getAllBookings(status, page, size), "OK"));
        }

        @GetMapping("/user/{userId}")
        @Operation(summary = "Lấy danh sách booking của một user")
        public ResponseEntity<ApiResponse<PageResponse<RentalGroupResponse>>> getByUser(
                        @PathVariable String userId,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size) {
                return ResponseEntity.ok(
                                ApiResponse.success(rentalGroupService.getBookingsByUser(userId, page, size), "OK"));
        }

        // ================================================================
        // STAFF ENDPOINTS
        // ================================================================

        @PatchMapping("/{id}/assign-driver")
        @Operation(summary = "[STAFF] Gán tài xế cho xe trong booking", description = """
                        Staff chọn tài xế đang trống lịch và gán vào RentalUnit.
                        - Chỉ áp dụng cho xe có `isWithDriver = true`.
                        - Tài xế phải đang ACTIVE và không có chuyến đang chạy.
                        - **Làm bước này TRƯỚC khi confirm**.
                        """)
        public ResponseEntity<ApiResponse<RentalGroupResponse>> assignDriver(
                        @PathVariable Long id,
                        @Valid @RequestBody AssignDriverRequest request) {
                return ResponseEntity.ok(ApiResponse.success(
                                rentalGroupService.assignDriver(id, request),
                                "Đã gán tài xế thành công."));
        }

        @PatchMapping("/{id}/confirm")
        @Operation(summary = "[STAFF] Xác nhận booking → CONFIRMED", description = """
                        Staff xác nhận đơn đặt xe sau khi kiểm tra.
                        - Nếu có xe cần tài xế: tất cả xe đó phải đã được gán tài xế.
                        - Sau xác nhận: tài xế đi lấy xe tại Hub rồi đến rước khách **hoặc** khách đến bãi nhận xe.
                        """)
        public ResponseEntity<ApiResponse<RentalGroupResponse>> confirm(@PathVariable Long id) {
                return ResponseEntity.ok(ApiResponse.success(
                                rentalGroupService.confirmBooking(id),
                                "Booking đã được xác nhận."));
        }

        @PatchMapping("/{id}/staff-handover-start")
        @Operation(summary = "[STAFF] Bàn giao xe cho khách tại bãi → IN_PROGRESS", description = """
                        Dùng cho booking **không có tài xế**.
                        Sau khi khách đến bãi và hoàn tất thủ tục, Staff ghi biên bản bàn giao và bắt đầu chuyến đi.
                        - Tạo `HandoverProtocol` type=**PICKUP**.
                        - RentalUnit → **ACTIVE**, booking → **IN_PROGRESS**.
                        """)
        public ResponseEntity<ApiResponse<RentalGroupResponse>> staffHandoverStart(
                        @PathVariable Long id,
                        @Valid @RequestBody HandoverRequest request) {
                return ResponseEntity.ok(ApiResponse.success(
                                rentalGroupService.staffHandoverStart(id, request),
                                "Đã bàn giao xe, chuyến đi bắt đầu."));
        }

        @PatchMapping("/{id}/staff-handover-return")
        @Operation(summary = "[STAFF] Nhận xe lại từ khách → COMPLETED", description = """
                        Dùng cho booking **không có tài xế**.
                        Khách trả xe tại bãi, Staff kiểm tra và ghi biên bản nhận xe.
                        - Tạo `HandoverProtocol` type=**RETURN**.
                        - RentalUnit → **RETURNED**, booking → **COMPLETED**.
                        """)
        public ResponseEntity<ApiResponse<RentalGroupResponse>> staffHandoverReturn(
                        @PathVariable Long id,
                        @Valid @RequestBody HandoverRequest request) {
                return ResponseEntity.ok(ApiResponse.success(
                                rentalGroupService.staffHandoverReturn(id, request),
                                "Đã nhận xe lại, booking hoàn tất."));
        }

        // ================================================================
        // DRIVER ENDPOINTS
        // ================================================================

        @PatchMapping("/{id}/driver-pickup-confirmed")
        @Operation(summary = "[DRIVER] Xác nhận đã đón khách → IN_PROGRESS", description = """
                        Dùng cho booking **có tài xế**.
                        Tài xế đến điểm đón, đón khách và xác nhận bắt đầu chuyến đi.
                        - Tạo `HandoverProtocol` type=**PICKUP** (ghi km lúc bắt đầu).
                        - RentalUnit → **ACTIVE**, booking → **IN_PROGRESS**.
                        """)
        public ResponseEntity<ApiResponse<RentalGroupResponse>> driverPickupConfirmed(
                        @PathVariable Long id,
                        @Valid @RequestBody HandoverRequest request) {
                return ResponseEntity.ok(ApiResponse.success(
                                rentalGroupService.driverConfirmPickup(id, request),
                                "Tài xế đã xác nhận đón khách. Chuyến đi bắt đầu!"));
        }

        @PatchMapping("/{id}/driver-complete-trip")
        @Operation(summary = "[DRIVER] Hoàn thành chuyến đi → COMPLETED", description = """
                        Dùng cho booking **có tài xế**.
                        Khách đã đến điểm đến. Tài xế trả xe về Hub và kết thúc chuyến.
                        - Tạo `HandoverProtocol` type=**RETURN** (ghi km lúc kết thúc).
                        - RentalUnit → **RETURNED**, booking → **COMPLETED**.
                        """)
        public ResponseEntity<ApiResponse<RentalGroupResponse>> driverCompleteTrip(
                        @PathVariable Long id,
                        @Valid @RequestBody HandoverRequest request) {
                return ResponseEntity.ok(ApiResponse.success(
                                rentalGroupService.driverCompleteTrip(id, request),
                                "Chuyến đi hoàn tất!"));
        }

        // ================================================================
        // HELPER ENDPOINT
        // ================================================================

        @GetMapping("/available-drivers")
        @Operation(summary = "[STAFF] Lấy danh sách tài xế đang trống lịch", description = "Trả về tài xế ACTIVE và không có booking nào đang PENDING/ACTIVE.")
        public ResponseEntity<ApiResponse<List<DriverProfileResponse>>> getAvailableDrivers() {
                return ResponseEntity.ok(ApiResponse.success(
                                rentalGroupService.getAvailableDrivers(),
                                "Danh sách tài xế có thể phân công."));
        }
}
