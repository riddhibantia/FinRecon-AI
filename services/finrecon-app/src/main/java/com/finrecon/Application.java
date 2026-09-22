package com.finrecon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Monolith: one Spring Boot app replaces the five microservices.
// All feature packages live under com.finrecon.* and are component-scanned.
@SpringBootApplication(scanBasePackages = "com.finrecon")
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
