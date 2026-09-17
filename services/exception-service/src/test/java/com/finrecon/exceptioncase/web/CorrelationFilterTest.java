package com.finrecon.exceptioncase.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// P11: correlation IDs are generated when absent and echoed when present.
class CorrelationFilterTest {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    @RestController
    static class PingController {
        @GetMapping("/ping")
        public Map<String, String> ping() {
            return Map.of("ok", "true");
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new PingController())
                .addFilters(new CorrelationFilter())
                .build();
    }

    @Test
    void generatesRequestIdWhenAbsent() throws Exception {
        MvcResult result = mvc.perform(get("/ping"))
                .andExpect(status().isOk())
                .andReturn();
        String header = result.getResponse().getHeader(CorrelationFilter.HEADER);
        assertNotNull(header);
        assertTrue(UUID_PATTERN.matcher(header).matches());
    }

    @Test
    void propagatesCallerSuppliedId() throws Exception {
        String supplied = UUID.randomUUID().toString();
        MvcResult result = mvc.perform(get("/ping").header(CorrelationFilter.HEADER, supplied))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(supplied, result.getResponse().getHeader(CorrelationFilter.HEADER));
    }
}
