package com.crs.bookingservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Mirror DTO của AdminUserResponse từ iam-service.
 * Chỉ map các field cần thiết cho booking-service.
 *
 * ⚠️ Phải dùng Boolean (wrapper) và @JsonProperty thay vì primitive boolean.
 * Lý do: Lombok @Data với primitive boolean tạo getter isXxx() → Jackson map
 * thành field "xxx"
 * nhưng IAM JSON trả về "isActive"/"isDeleted" → serializtion mismatch.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class IamUserDto {

    String userId;
    String email;
    String fullName;
    String phone;
    String gender;
    LocalDate dob;

    /**
     * ⚠️ Dùng Boolean (wrapper) + @JsonProperty để Jackson map đúng "isActive" từ
     * JSON.
     */
    @JsonProperty("isActive")
    Boolean isActive;

    @JsonProperty("isDeleted")
    Boolean isDeleted;

    LocalDateTime createdAt;
    RoleInfo role;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RoleInfo {
        Long id;
        String name;
        Set<String> permissions;
    }
}
