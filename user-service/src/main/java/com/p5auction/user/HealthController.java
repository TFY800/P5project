package com.p5auction.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final String serviceName;

    public HealthController(@Value("${spring.application.name}") String serviceName) {
        this.serviceName = serviceName;
    }

    @GetMapping("/api/health")
    public HealthResponse health() {
        return new HealthResponse(serviceName, "UP");
    }

    public record HealthResponse(String service, String status) {}
}
