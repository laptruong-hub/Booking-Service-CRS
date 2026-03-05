package com.crs.bookingservice.enums;

/**
 * Trạng thái của một xe đơn lẻ trong nhóm thuê
 */
public enum RentalUnitStatus {
    PENDING, // Chờ bàn giao
    ACTIVE, // Đang sử dụng
    RETURNED, // Đã trả xe
    CANCELLED // Đã huỷ xe này trong nhóm
}
