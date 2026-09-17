package com.finrecon.reconciliation.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

// P11: the live CORS boundary. Dashboard origin passes preflight; any
// other origin is refused. Full context (H2): proves the real config.
@SpringBootTest
@AutoConfigureMockMvc
class CorsPreflightTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void dashboardOriginPassesPreflight() throws Exception {
        mvc.perform(options("/api/health")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin",
                        "http://localhost:3000"));
    }

    @Test
    void foreignOriginIsRefused() throws Exception {
        mvc.perform(options("/api/health")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
