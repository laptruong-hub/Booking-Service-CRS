package com.crs.bookingservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Mirror của Page<VehicleResponse> từ car-management.
 *
 * Spring Page khi serialize ra JSON có NHIỀU field hơn (pageable, sort,
 * numberOfElements...).
 * 
 * @JsonIgnoreProperties(ignoreUnknown = true) bắt buộc để tránh
 *                                     DeserializeException.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VehiclePageDto {
    private List<VehicleDto> content;
    private long totalElements;
    private int totalPages;
    private int number;
    private int size;
    private boolean first;
    private boolean last;
}
