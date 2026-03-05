package com.crs.bookingservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Feign configuration — đăng ký JavaTimeModule vào ObjectMapper để
 * LocalDate, LocalDateTime serialize/deserialize đúng khi gọi giữa các
 * services.
 *
 * Spring Cloud OpenFeign tự động dùng ObjectMapper bean này — không cần
 * khai báo JacksonDecoder / JacksonEncoder thủ công.
 */
@Configuration
public class FeignConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
