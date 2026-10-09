package com.isd.wms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.isd.wms.controller.ReplenishmentController;
import com.isd.wms.dto.replenishment.ReplenishmentResponse;
import com.isd.wms.dto.replenishment.ReplenishmentSearchRequest;
import com.isd.wms.entity.Location;
import com.isd.wms.entity.Product;
import com.isd.wms.entity.Replenishment;
import com.isd.wms.enums.Role;
import com.isd.wms.enums.Status;
import com.isd.wms.exception.GlobalExceptionHandler;
import com.isd.wms.mapper.ReplenishmentMapper;
import com.isd.wms.repository.ReplenishmentRepository;
import com.isd.wms.repository.TransportUnitRepository;
import com.isd.wms.service.ReplenishmentService;
import com.isd.wms.service.validation.SecurityFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig
@ContextConfiguration(classes = Def17ReplenishmentSecurityRemediationTest.TestConfig.class)
class Def17ReplenishmentSecurityRemediationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ReplenishmentService mockReplenishmentService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Isolated unit-test fixtures for service-level defense-in-depth
    private ReplenishmentRepository unitReplenishmentRepository;
    private ReplenishmentService unitReplenishmentService;
    private String currentUsername;
    private Role currentRole;
    private AtomicBoolean accessibleBySupervisorCalled;
    private AtomicBoolean findAllCalled;
    private Replenishment aliceReplenishment;
    private Replenishment bobReplenishment;

    @SuppressWarnings("unchecked")
    private static <T> T createProxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    @BeforeEach
    void setUp() {
        reset(mockReplenishmentService);
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        currentUsername = "supervisor_alice";
        currentRole = Role.ROLE_SUPERVISOR;
        accessibleBySupervisorCalled = new AtomicBoolean(false);
        findAllCalled = new AtomicBoolean(false);

        Product p = new Product();
        ReflectionTestUtils.setField(p, "id", 10L);
        p.setName("Widget A");

        Location loc = new Location();
        ReflectionTestUtils.setField(loc, "id", 20L);
        loc.setBarcode("PICK-01");

        aliceReplenishment = new Replenishment(p, 50, loc, "supervisor_alice");
        ReflectionTestUtils.setField(aliceReplenishment, "id", 101L);
        aliceReplenishment.setLogicId("REP-ALICE");
        aliceReplenishment.setStatus(Status.CREATED);

        bobReplenishment = new Replenishment(p, 30, loc, "supervisor_bob");
        ReflectionTestUtils.setField(bobReplenishment, "id", 102L);
        bobReplenishment.setLogicId("REP-BOB");
        bobReplenishment.setStatus(Status.CREATED);

        unitReplenishmentRepository = createProxy(ReplenishmentRepository.class, (proxy, method, args) -> {
            String name = method.getName();
            if ("findAllAccessibleBySupervisor".equals(name)) {
                accessibleBySupervisorCalled.set(true);
                return List.of(aliceReplenishment);
            }
            if ("findAll".equals(name)) {
                findAllCalled.set(true);
                return List.of(aliceReplenishment, bobReplenishment);
            }
            if ("filter".equals(name)) {
                return List.of(aliceReplenishment, bobReplenishment);
            }
            return null;
        });

        TransportUnitRepository tuRepository = createProxy(TransportUnitRepository.class, (proxy, method, args) -> Collections.emptyList());
        ReplenishmentMapper replenishmentMapper = new ReplenishmentMapper(tuRepository);

        SecurityFacade securityFacade = new SecurityFacade(null) {
            @Override
            public String getCurrentUsername() {
                return currentUsername;
            }

            @Override
            public boolean hasRole(Role role) {
                return currentRole == role;
            }
        };

        unitReplenishmentService = new ReplenishmentService(
            unitReplenishmentRepository,
            null,
            null,
            null,
            null,
            tuRepository,
            replenishmentMapper,
            null,
            null,
            null,
            securityFacade
        );
    }

    // ==========================================
    // 1. Controller HTTP Security via MockMvc
    // ==========================================

    @Test
    @WithMockUser(roles = "OPERATOR")
    @DisplayName("HTTP Security: OPERATOR cannot view all replenishments (403 Forbidden)")
    void operator_getAllReplenishments_httpForbidden() throws Exception {
        mockMvc.perform(get("/api/replenishments"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    @DisplayName("HTTP Security: OPERATOR cannot view replenishment by id (403 Forbidden)")
    void operator_getReplenishmentById_httpForbidden() throws Exception {
        mockMvc.perform(get("/api/replenishments/101"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    @DisplayName("HTTP Security: OPERATOR cannot search replenishments (403 Forbidden)")
    void operator_searchReplenishments_httpForbidden() throws Exception {
        ReplenishmentSearchRequest request = new ReplenishmentSearchRequest(null, null, null, null, null, null);
        mockMvc.perform(post("/api/replenishments/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    @DisplayName("HTTP Security: SUPERVISOR can access replenishments (200 OK)")
    void supervisor_getAllReplenishments_httpOk() throws Exception {
        when(mockReplenishmentService.getAllReplenishments()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/replenishments"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DEV")
    @DisplayName("HTTP Security: DEV can access replenishments (200 OK)")
    void dev_getAllReplenishments_httpOk() throws Exception {
        when(mockReplenishmentService.getAllReplenishments()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/replenishments"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("HTTP Security: Unauthenticated access to replenishments is forbidden")
    void unauthenticated_getAllReplenishments_httpForbidden() throws Exception {
        mockMvc.perform(get("/api/replenishments"))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // 2. Service Defense-in-Depth Layer
    // ==========================================

    @Test
    @DisplayName("Service defense-in-depth: OPERATOR role calling getAllReplenishments throws AccessDeniedException")
    void operator_callingGetAllReplenishments_denied() {
        currentUsername = "operator_dave";
        currentRole = Role.ROLE_OPERATOR;

        assertThatThrownBy(() -> unitReplenishmentService.getAllReplenishments())
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("only SUPERVISOR or DEV can view replenishments");

        assertThat(findAllCalled.get()).isFalse();
        assertThat(accessibleBySupervisorCalled.get()).isFalse();
    }

    @Test
    @DisplayName("Service defense-in-depth: OPERATOR role calling searchReplenishments throws AccessDeniedException")
    void operator_callingSearchReplenishments_denied() {
        currentUsername = "operator_dave";
        currentRole = Role.ROLE_OPERATOR;

        ReplenishmentSearchRequest request = new ReplenishmentSearchRequest(null, null, null, null, null, null);

        assertThatThrownBy(() -> unitReplenishmentService.searchReplenishments(request))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("only SUPERVISOR or DEV can search replenishments");
    }

    @Test
    @DisplayName("Service defense-in-depth: SUPERVISOR calling getAllReplenishments uses accessible-by-supervisor scoping")
    void supervisor_callingGetAllReplenishments_scopedToOwnData() {
        currentUsername = "supervisor_alice";
        currentRole = Role.ROLE_SUPERVISOR;

        List<ReplenishmentResponse> result = unitReplenishmentService.getAllReplenishments();

        assertThat(accessibleBySupervisorCalled.get()).isTrue();
        assertThat(findAllCalled.get()).isFalse();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(101L);
    }

    @Test
    @DisplayName("Service defense-in-depth: SUPERVISOR calling searchReplenishments isolates own replenishments only")
    void supervisor_callingSearchReplenishments_filtersOutOtherSupervisors() {
        currentUsername = "supervisor_alice";
        currentRole = Role.ROLE_SUPERVISOR;

        ReplenishmentSearchRequest request = new ReplenishmentSearchRequest(null, null, null, null, null, null);
        List<ReplenishmentResponse> result = unitReplenishmentService.searchReplenishments(request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(101L);
    }

    @Test
    @DisplayName("Service defense-in-depth: DEV calling getAllReplenishments and searchReplenishments has global access")
    void dev_hasGlobalAccess() {
        currentUsername = "dev_admin";
        currentRole = Role.ROLE_DEV;

        List<ReplenishmentResponse> all = unitReplenishmentService.getAllReplenishments();
        assertThat(findAllCalled.get()).isTrue();
        assertThat(all).hasSize(2);

        ReplenishmentSearchRequest request = new ReplenishmentSearchRequest(null, null, null, null, null, null);
        List<ReplenishmentResponse> search = unitReplenishmentService.searchReplenishments(request);
        assertThat(search).hasSize(2);
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
            http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
            return http.build();
        }
    }
}
