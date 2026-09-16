package com.finrecon.exceptioncase;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// P0 skeleton only. No exception or case management. No DB.
@SpringBootApplication(scanBasePackages = "com.finrecon.exceptioncase")
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
