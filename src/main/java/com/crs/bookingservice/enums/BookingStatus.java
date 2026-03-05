package com.crs.bookingservice.enums;

/**
 * Trạng thái của nhóm đặt xe (Rental Group / booking)
 */
public enum BookingStatus {
    PENDING, // Chờ xác nhận
    CONFIRMED, // Đã xác nhận, chờ bàn giao xe
    IN_PROGRESS, // Đang thuê xe
    COMPLETED, // Hoàn tất
    CANCELLED, // Đã huỷ
    OVERDUE // Quá hạn trả xe
}
