package com.crs.bookingservice.client;

import com.crs.bookingservice.client.dto.IamUserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * Feign client giao tiếp với iam-service qua /internal endpoints (không cần
 * JWT).
 * URL được cấu hình trong application.yml: feign.clients.iam-service.url
 */
@FeignClient(name = "iam-service", url = "${feign.clients.iam-service.url}")
public interface IamServiceClient {

    /**
     * Lấy thông tin user theo userId.
     * → GET http://localhost:8080/internal/users/{userId}
     */
    @GetMapping("/internal/users/{userId}")
    IamUserDto getUserById(@PathVariable("userId") String userId);

    /**
     * Kiểm tra user có tồn tại không.
     * → GET http://localhost:8080/internal/users/{userId}/exists
     */
    @GetMapping("/internal/users/{userId}/exists")
    Boolean existsById(@PathVariable("userId") String userId);

    /**
     * Lấy tất cả active users theo role name (ví dụ: "DRIVER").
     * → GET http://localhost:8080/internal/users/role/{roleName}
     */
    @GetMapping("/internal/users/role/{roleName}")
    List<IamUserDto> getUsersByRole(@PathVariable("roleName") String roleName);
}
