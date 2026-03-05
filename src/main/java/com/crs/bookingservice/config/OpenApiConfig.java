package com.crs.bookingservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bookingServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Booking Service API")
                        .description("API quản lý đặt xe thuê — Car Rental System (CRS)")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("CRS Team")
                                .email("admin@rental.com")));
    }
}
