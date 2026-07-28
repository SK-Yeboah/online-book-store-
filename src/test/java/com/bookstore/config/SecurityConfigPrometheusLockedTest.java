package com.bookstore.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "security.actuator.prometheus-public=false",
        "management.endpoints.web.exposure.include=health,prometheus"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("SecurityConfig — prometheus locked down")
class SecurityConfigPrometheusLockedTest {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("GET /actuator/prometheus without auth → 401")
    void prometheus_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /actuator/prometheus as USER → 403")
    @WithMockUser(roles = "USER")
    void prometheus_asUser_returns403() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /actuator/prometheus as ADMIN → not 401/403")
    @WithMockUser(roles = "ADMIN")
    void prometheus_asAdmin_isAllowedBySecurity() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).isNotEqualTo(401);
                    assertThat(status).isNotEqualTo(403);
                });
    }
}
