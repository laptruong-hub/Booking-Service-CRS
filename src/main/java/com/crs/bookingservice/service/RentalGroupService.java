package com.crs.bookingservice.service;

import com.crs.bookingservice.dto.request.AssignDriverRequest;
import com.crs.bookingservice.dto.request.CreateRentalGroupRequest;
import com.crs.bookingservice.dto.request.HandoverRequest;
import com.crs.bookingservice.dto.request.StaffHandoverScanRequest;
import com.crs.bookingservice.dto.response.DriverProfileResponse;
import com.crs.bookingservice.dto.response.HandoverAiPreviewResponse;
import com.crs.bookingservice.dto.response.PageResponse;
import com.crs.bookingservice.dto.response.RentalGroupResponse;
import com.crs.bookingservice.enums.BookingStatus;

import java.util.List;

public interface RentalGroupService {

    // ============================================================
    // CUSTOMER OPERATIONS
    // ============================================================

    /** Khách tạo booking */
    RentalGroupResponse createBooking(CreateRentalGroupRequest request);

    /** Khách huỷ booking */
    RentalGroupResponse cancelBooking(Long id, String reason);

    // ============================================================
    // QUERY OPERATIONS
    // ============================================================

    RentalGroupResponse getBookingById(Long id);

    RentalGroupResponse getBookingByCode(String bookingCode);

    PageResponse<RentalGroupResponse> getBookingsByUser(String userId, int page, int size);

    PageResponse<RentalGroupResponse> getAllBookings(BookingStatus status, int page, int size);

    // ============================================================
    // STAFF OPERATIONS
    // ============================================================

    /**
     * [STAFF] Gán tài xế cho một xe trong booking (chỉ áp dụng khi
     * isWithDriver=true).
     * Sau bước này, staff gọi tiếp confirmBooking().
     */
    RentalGroupResponse assignDriver(Long bookingId, AssignDriverRequest request);

    /**
     * [STAFF] Xác nhận booking:
     * - Không tài xế: gửi thông báo yêu cầu khách đến bãi → CONFIRMED
     * - Có tài xế: tài xế đã được gán → CONFIRMED, tài xế bắt đầu đi lấy xe
     */
    RentalGroupResponse confirmBooking(Long id);

    /**
     * [STAFF] Bàn giao xe cho khách tại bãi (trường hợp không có tài xế).
     * Tạo HandoverProtocol type=PICKUP và chuyển sang IN_PROGRESS.
     */
    RentalGroupResponse staffHandoverStart(Long bookingId, HandoverRequest request);

    /**
     * [STAFF] Scan AI cho bàn giao PICKUP trước khi confirm.
     * Không đổi trạng thái booking/rental unit, nhưng có lưu analysis record.
     */
    HandoverAiPreviewResponse previewStaffHandoverStart(Long bookingId, StaffHandoverScanRequest request);

    /**
     * [STAFF] Nhận xe lại từ khách sau chuyến đi.
     * Tạo HandoverProtocol type=RETURN và chuyển sang COMPLETED.
     */
    RentalGroupResponse staffHandoverReturn(Long bookingId, HandoverRequest request);

    /**
     * [STAFF] Scan AI cho nhận xe RETURN trước khi confirm.
     * Không đổi trạng thái booking/rental unit, nhưng có lưu analysis record.
     */
    HandoverAiPreviewResponse previewStaffHandoverReturn(Long bookingId, StaffHandoverScanRequest request);

    // ============================================================
    // DRIVER OPERATIONS
    // ============================================================

    /**
     * [DRIVER] Xác nhận đã đón được khách → trip bắt đầu → IN_PROGRESS.
     * Tạo HandoverProtocol type=PICKUP.
     */
    RentalGroupResponse driverConfirmPickup(Long bookingId, HandoverRequest request);

    /**
     * [DRIVER] Xác nhận đã trả xe về Hub sau khi kết thúc chuyến → COMPLETED.
     * Tạo HandoverProtocol type=RETURN.
     */
    RentalGroupResponse driverCompleteTrip(Long bookingId, HandoverRequest request);

    // ============================================================
    // DRIVER PROFILE OPERATIONS
    // ============================================================

    /** Lấy danh sách tài xế đang trống lịch (ACTIVE, không có booking đang chạy) */
    List<DriverProfileResponse> getAvailableDrivers();
}
