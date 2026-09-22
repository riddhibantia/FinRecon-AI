package com.finrecon;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Single health contract for the monolith. The old per-service health
// endpoints (8081-8084) are gone; this answers on 8080.
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "finrecon-app");
    }
}
