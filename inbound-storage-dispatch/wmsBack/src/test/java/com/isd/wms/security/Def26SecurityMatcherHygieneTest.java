package com.isd.wms.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests verifying Spring Security filter chain hygiene (remediating DEF-26).
 *
 * <p>Executes real HTTP requests through the full Spring Security filter chain to verify:
 * 1. Unauthenticated requests to non-existent /api/operator/** paths are denied at the filter chain level.
 * 2. Authenticated requests to /api/operator/** pass filter chain but receive 404 (proving no dead controllers exist).
 * 3. /api/supervisor/** endpoints are strictly blocked for OPERATOR role by the filter chain.
 * 4. /api/supervisor/** endpoints are accessible to SUPERVISOR role.
 * 5. General protected APIs enforce authentication via .anyRequest().authenticated().</p>
 */
@SpringBootTest
class Def26SecurityMatcherHygieneTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("DEF-26: Unauthenticated access to /api/operator/** path is denied by SecurityFilterChain")
    void unauthenticatedRequestToDeadOperatorPath_isDeniedBySecurityFilterChain() throws Exception {
        mockMvc.perform(get("/api/operator/tasks"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    @DisplayName("DEF-26: Authenticated OPERATOR request to /api/operator/** passes filter chain and returns 404 (no controller exists)")
    void authenticatedOperatorRequestToDeadOperatorPath_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/operator/tasks"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    @DisplayName("DEF-26: OPERATOR accessing /api/supervisor/** is strictly forbidden by SecurityFilterChain")
    void operatorRequestToSupervisorPath_isForbiddenBySecurityFilterChain() throws Exception {
        mockMvc.perform(get("/api/supervisor/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    @DisplayName("DEF-26: SUPERVISOR accessing /api/supervisor/** passes filter chain and reaches controller")
    void supervisorRequestToSupervisorPath_isAllowedBySecurityFilterChain() throws Exception {
        mockMvc.perform(get("/api/supervisor/dashboard"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DEF-26: Unauthenticated access to protected API is rejected by .anyRequest().authenticated()")
    void unauthenticatedRequestToProtectedApi_isRejected() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isForbidden());
    }
}
