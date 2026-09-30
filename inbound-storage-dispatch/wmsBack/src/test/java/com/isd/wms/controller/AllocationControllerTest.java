package com.isd.wms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.isd.wms.dto.allocation.AllocationExecutionResponse;
import com.isd.wms.dto.operator.OperatorTaskSummaryResponse;
import com.isd.wms.service.AllocationExecutionService;
import com.isd.wms.service.AllocationService;
import com.isd.wms.service.TransportUnitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig
@ContextConfiguration(classes = AllocationControllerTest.TestConfig.class)
class AllocationControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private AllocationExecutionService allocationExecutionService;

    @Autowired
    private AllocationService allocationService;

    @Autowired
    private TransportUnitService transportUnitService;

    private OperatorTaskSummaryResponse mockSummary;

    @BeforeEach
    void setUp() {
        reset(allocationExecutionService, allocationService, transportUnitService);
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .build();

        mockSummary = new OperatorTaskSummaryResponse(
            100L, 200L, "ORD-1", null, "PICKING_ORDER", "DISP-01", 1, 0, false, false, null, List.of(), List.of()
        );
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void getCurrentSummary_ShouldReturn200AndSummary() throws Exception {
        when(allocationExecutionService.getCurrentSummary()).thenReturn(Optional.of(mockSummary));

        mockMvc.perform(get("/api/v1/allocations/operator/current/summary")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskId").value(100L))
            .andExpect(jsonPath("$.taskType").value("PICKING_ORDER"));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void getCurrentSummary_Empty_ShouldReturn204NoContent() throws Exception {
        when(allocationExecutionService.getCurrentSummary()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/allocations/operator/current/summary")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void startCurrentTask_ShouldReturn200AndSummary() throws Exception {
        when(allocationExecutionService.startCurrentTask()).thenReturn(mockSummary);

        mockMvc.perform(post("/api/v1/allocations/operator/current/start")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskId").value(100L));
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void getCurrentSummary_WithWrongRole_ShouldReturn403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/allocations/operator/current/summary")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestConfig {

        @Bean
        public AllocationService allocationService() {
            return mock(AllocationService.class);
        }

        @Bean
        public AllocationExecutionService allocationExecutionService() {
            return mock(AllocationExecutionService.class);
        }

        @Bean
        public TransportUnitService transportUnitService() {
            return mock(TransportUnitService.class);
        }

        @Bean
        public AllocationController allocationController(AllocationService allocationService, AllocationExecutionService allocationExecutionService, TransportUnitService transportUnitService) {
            return new AllocationController(allocationService, allocationExecutionService, transportUnitService);
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
            return http.build();
        }
    }
}
