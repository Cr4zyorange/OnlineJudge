package com.onlinejudge.authservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "onlinejudge.auth.seed-data-enabled=false",
        "onlinejudge.build.version=0.1.0-test",
        "onlinejudge.build.revision=abc123"
})
@AutoConfigureMockMvc
class AuthSystemControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesMinimalHealthReadinessAndVersion() throws Exception {
        mockMvc.perform(get("/api/v1/system/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"));

        mockMvc.perform(get("/api/v1/system/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"));

        mockMvc.perform(get("/api/v1/system/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.service").value("auth-service"))
                .andExpect(jsonPath("$.data.version").value("0.1.0-test"))
                .andExpect(jsonPath("$.data.revision").value("abc123"));
    }
}
