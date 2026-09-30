package com.isd.wms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.isd.wms.dto.replenishment.ReplenishmentCreateRequest;
import com.isd.wms.dto.replenishment.ReplenishmentResponse;
import com.isd.wms.dto.replenishment.ReplenishmentSearchRequest;
import com.isd.wms.dto.replenishment.ReplenishmentUpdateRequest;
import com.isd.wms.enums.Status;
import com.isd.wms.exception.GlobalExceptionHandler;
import com.isd.wms.exception.ReplenishmentNotFoundException;
import com.isd.wms.service.ReplenishmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig
@ContextConfiguration(classes = ReplenishmentControllerTest.TestConfig.class)
class ReplenishmentControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ReplenishmentService replenishmentService;

    @BeforeEach
    void setUp() {
        reset(replenishmentService);
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void createReplenishment_withSupervisorRole_returnsCreated() throws Exception {
        ReplenishmentCreateRequest request = new ReplenishmentCreateRequest(1L, 10, 3L);
        ReplenishmentResponse response = new ReplenishmentResponse(1L, 1L, 2L, 10, Status.CREATED, 3L, null);

        when(replenishmentService.createReplenishment(any(ReplenishmentCreateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/replenishments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void createReplenishment_withOperatorRole_returnsForbidden() throws Exception {
        ReplenishmentCreateRequest request = new ReplenishmentCreateRequest(1L, 10, 3L);

        mockMvc.perform(post("/api/replenishments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createReplenishment_unauthenticated_returnsUnauthorized() throws Exception {
        ReplenishmentCreateRequest request = new ReplenishmentCreateRequest(1L, 10, 3L);

        mockMvc.perform(post("/api/replenishments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void getAllReplenishments_returnsOkWithList() throws Exception {
        ReplenishmentResponse r1 = new ReplenishmentResponse(1L, 1L, 2L, 10, Status.CREATED, 3L, null);
        ReplenishmentResponse r2 = new ReplenishmentResponse(2L, 2L, 3L, 5, Status.IN_PROGRESS, 4L, null);

        when(replenishmentService.getAllReplenishments()).thenReturn(List.of(r1, r2));

        mockMvc.perform(get("/api/replenishments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[1].id").value(2L));
    }

    @Test
    @WithMockUser
    void getReplenishmentById_existingId_returnsOk() throws Exception {
        ReplenishmentResponse response = new ReplenishmentResponse(1L, 1L, 2L, 10, Status.CREATED, 3L, null);

        when(replenishmentService.getReplenishmentById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/replenishments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    @WithMockUser
    void getReplenishmentById_notFound_returnsNotFound() throws Exception {
        when(replenishmentService.getReplenishmentById(99L))
                .thenThrow(new ReplenishmentNotFoundException(99L));

        mockMvc.perform(get("/api/replenishments/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void updateReplenishment_validRequest_returnsOk() throws Exception {
        ReplenishmentUpdateRequest request = new ReplenishmentUpdateRequest(1L, 15L,24, Status.COMPLETED, 3L);
        ReplenishmentResponse response = new ReplenishmentResponse(1L, 1L, 2L, 15, Status.COMPLETED, 3L, null);

        when(replenishmentService.updateReplenishment(eq(1L), any(ReplenishmentUpdateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/replenishments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void deleteReplenishment_existingId_returnsNoContent() throws Exception {
        doNothing().when(replenishmentService).deleteReplenishment(1L);

        mockMvc.perform(delete("/api/replenishments/1"))
                .andExpect(status().isNoContent());

        verify(replenishmentService).deleteReplenishment(1L);
    }

    @Test
    @WithMockUser
    void searchReplenishments_returnsMatchingList() throws Exception {
        ReplenishmentSearchRequest request = new ReplenishmentSearchRequest(1L, null,12, Status.CREATED, null);
        ReplenishmentResponse response = new ReplenishmentResponse(1L, 1L, 2L, 10, Status.CREATED, 3L, null);

        when(replenishmentService.searchReplenishments(any(ReplenishmentSearchRequest.class)))
                .thenReturn(List.of(response));

        mockMvc.perform(post("/api/replenishments/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("CREATED"));
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    @Import(GlobalExceptionHandler.class)
    static class TestConfig {

        @Bean
        public ReplenishmentService replenishmentService() {
            return mock(ReplenishmentService.class);
        }

        @Bean
        public ReplenishmentController replenishmentController(ReplenishmentService replenishmentService) {
            return new ReplenishmentController(replenishmentService);
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
            return http.build();
        }
    }
}
