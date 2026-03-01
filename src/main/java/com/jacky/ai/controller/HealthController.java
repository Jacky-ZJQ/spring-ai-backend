package com.jacky.ai.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final HealthEndpoint healthEndpoint;

    @Value("${spring.application.name:spring-ai-demo}")
    private String applicationName;

    @GetMapping({"/health", "/ai/health"})
    public Map<String, Object> health() {
        HealthComponent healthComponent = healthEndpoint.health();
        String status = healthComponent.getStatus().getCode();
        boolean up = Status.UP.getCode().equalsIgnoreCase(status);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", up ? 1 : 0);
        response.put("msg", up ? "ok" : "service not healthy");
        response.put("service", applicationName);
        response.put("status", status);
        response.put("time", LocalDateTime.now().toString());
        return response;
    }

}
