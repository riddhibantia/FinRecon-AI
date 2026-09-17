package com.finrecon.exceptioncase.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// P11 integration: browsers may call /api/** only from the configured
// dashboard origin, with GET/POST only. No cookies, no credentials, no
// PUT/DELETE anywhere. Service-to-service calls are server-side and
// unaffected by CORS.
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String frontendOrigin;

    public WebConfig(
            @Value("${finrecon.frontend.origin:http://localhost:3000}") String frontendOrigin) {
        this.frontendOrigin = frontendOrigin;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(frontendOrigin)
                .allowedMethods("GET", "POST")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
