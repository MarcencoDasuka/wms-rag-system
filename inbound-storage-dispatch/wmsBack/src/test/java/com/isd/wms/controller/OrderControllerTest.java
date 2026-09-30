package com.isd.wms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.isd.wms.dto.order.*;
import com.isd.wms.dto.order_line.OrderLineCreateRequest;
import com.isd.wms.dto.order_line.OrderLineResponse;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.enums.Status;
import com.isd.wms.exception.GlobalExceptionHandler;
import com.isd.wms.exception.OrderNotFoundException;
import com.isd.wms.service.OrderService;
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

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig
@ContextConfiguration(classes = OrderControllerTest.TestConfig.class)
class OrderControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private OrderService orderService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String BASE_URL = "/api/v1/orders";

    @BeforeEach
    void setUp() {
        reset(orderService);
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void getOrders_asSupervisor_returns200AndList() throws Exception {
        OrderResponse response = new OrderResponse(1L, "LOGIC-001", 1L, OrderStatus.CREATED, null, null);
        when(orderService.getAllOrders()).thenReturn(List.of(response));

        mockMvc.perform(get(BASE_URL).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].logicId").value("LOGIC-001"));
    }

    @Test
    @WithMockUser(roles = "DEV")
    void getOrders_asDevRole_returns200() throws Exception {
        when(orderService.getAllOrders()).thenReturn(List.of());

        mockMvc.perform(get(BASE_URL).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_WORKER")
    void getOrders_forbiddenRole_returns403() throws Exception {
        mockMvc.perform(get(BASE_URL).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void getOrders_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get(BASE_URL).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void getOrderById_existingId_returns200() throws Exception {
        OrderResponse response = new OrderResponse(1L, "LOGIC-001", 1L, OrderStatus.CREATED, null, null);
        when(orderService.getOrderById(1L)).thenReturn(response);

        mockMvc.perform(get(BASE_URL + "/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.logicId").value("LOGIC-001"));
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void getOrderById_notFound_returns404() throws Exception {
        when(orderService.getOrderById(99L)).thenThrow(new OrderNotFoundException(99L));

        mockMvc.perform(get(BASE_URL + "/99").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void getExtendedOrders_returns200AndList() throws Exception {
        when(orderService.getAllExtendedOrders()).thenReturn(List.of());

        mockMvc.perform(get(BASE_URL + "/extended").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void getExtendedOrderById_existingId_returns200() throws Exception {
        ExtendedOrderResponse response = new ExtendedOrderResponse(
            new OrderResponse(1L, "LOGIC-001", 1L, OrderStatus.CREATED, null, null),
            new ArrayList<OrderLineResponse>(),
            0
        );

        when(orderService.getExtendedOrderById(1L)).thenReturn(response);

        mockMvc.perform(get(BASE_URL + "/extended/1").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.order.id").value(1L));
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void getExtendedOrderById_notFound_returns404() throws Exception {
        when(orderService.getExtendedOrderById(99L)).thenThrow(new OrderNotFoundException(99L));

        mockMvc.perform(get(BASE_URL + "/extended/99").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void createOrder_validRequest_returns201() throws Exception {
        ExtendedOrderCreateRequest request = new ExtendedOrderCreateRequest(
                new OrderCreateRequest("LOGIC-001", 1L), new ArrayList<OrderLineCreateRequest>()
        );
        OrderResponse response = new OrderResponse(1L, "LOGIC-001", 1L, OrderStatus.CREATED, null, null);
        when(orderService.addExtendedOrder(any(ExtendedOrderCreateRequest.class))).thenReturn(response);

        mockMvc.perform(post(BASE_URL)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void updateOrder_validRequest_returns200() throws Exception {
        OrderUpdateRequest request = new OrderUpdateRequest("LOGIC-002", 1L, OrderStatus.CREATED);
        OrderResponse response = new OrderResponse(1L, "LOGIC-002", 1L, OrderStatus.CREATED, null, null);
        when(orderService.updateOrder(eq(1L), any(OrderUpdateRequest.class))).thenReturn(response);

        mockMvc.perform(put(BASE_URL + "/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logicId").value("LOGIC-002"));
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void updateOrder_notFound_returns404() throws Exception {
        OrderUpdateRequest request = new OrderUpdateRequest("LOGIC-002", 1L, OrderStatus.CREATED);
        when(orderService.updateOrder(eq(99L), any())).thenThrow(new OrderNotFoundException(99L));

        mockMvc.perform(put(BASE_URL + "/99")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void deleteOrder_existingId_returns204() throws Exception {
        doNothing().when(orderService).deleteOrderById(1L);

        mockMvc.perform(delete(BASE_URL + "/1").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void searchOrders_withParams_returns200AndList() throws Exception {
        OrderResponse response = new OrderResponse(1L, "LOGIC-001", 1L, OrderStatus.CREATED, null, null);
        when(orderService.searchOrders(any(OrderSearchRequest.class))).thenReturn(List.of(response));

        mockMvc.perform(get(BASE_URL + "/filter")
                        .param("status", "CREATED")
                        .param("logicId", "LOGIC-001")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L));
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void searchOrders_noMatch_returns200AndEmptyList() throws Exception {
        when(orderService.searchOrders(any(OrderSearchRequest.class))).thenReturn(List.of());

        mockMvc.perform(get(BASE_URL + "/filter")
                        .param("logicId", "NONEXISTENT")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void searchOrders_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get(BASE_URL + "/filter").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestConfig {

        @Bean
        public GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }

        @Bean
        public OrderService orderService() {
            return mock(OrderService.class);
        }

        @Bean
        public OrderController orderController(OrderService orderService) {
            return new OrderController(orderService);
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
            return http.build();
        }
    }
}
