package com.isd.wms.controller;

import com.isd.wms.entity.User;
import com.isd.wms.enums.Role;
import com.isd.wms.exception.GlobalExceptionHandler;
import com.isd.wms.exception.InvalidCredentialsException;
import com.isd.wms.service.AuthService;
import com.isd.wms.service.UserService;
import com.isd.wms.service.validation.SecurityFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig
@ContextConfiguration(classes = AuthControllerTest.TestConfig.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @Autowired
    private SecurityFacade securityFacade;

    @BeforeEach
    void setUp() {
        reset(authService, userService, securityFacade);
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void whenDataIsCorrect_thenLoginReturnsTokenJWTAndSetsHttpOnlyCookie() throws Exception {
        String loginPayload = "{\"username\":\"test_user\",\"password\":\"secret_password\"}";

        when(authService.authenticateAndGenerateToken("test_user", "secret_password"))
                .thenReturn("fake-jwt-token");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("fake-jwt-token"))
                .andExpect(cookie().exists("jwt_token"))
                .andExpect(cookie().httpOnly("jwt_token", true))
                .andExpect(cookie().path("jwt_token", "/"))
                .andExpect(cookie().value("jwt_token", "fake-jwt-token"));
    }

    @Test
    void whenPasswordIsIncorrect_thenReturn401Unauthorized() throws Exception {
        String loginPayload = "{\"username\":\"test_user\",\"password\":\"incorrect_password\"}";

        when(authService.authenticateAndGenerateToken(anyString(), anyString()))
                .thenThrow(new InvalidCredentialsException("Incorrect username or password."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload)
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Incorrect username or password."));
    }

    @Test
    void whenAccountIsInactive_thenReturn403Forbidden() throws Exception {
        String loginPayload = "{\"username\":\"inactive_user\",\"password\":\"password\"}";

        when(authService.authenticateAndGenerateToken("inactive_user", "password"))
                .thenThrow(new org.springframework.security.authentication.DisabledException("Account is inactive."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload)
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Account is inactive."));
    }

    @Test
    void whenEmailNotVerified_thenReturn403Forbidden() throws Exception {
        String loginPayload = "{\"username\":\"unverified_user\",\"password\":\"password\"}";

        when(authService.authenticateAndGenerateToken("unverified_user", "password"))
                .thenThrow(new com.isd.wms.exception.UserNotVerifiedException("Please verify your email before logging in."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload)
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Please verify your email before logging in."));
    }

    @Test
    void whenLogout_thenClearsJwtCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("jwt_token", 0))
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }

    @Test
    void whenGetMe_thenReturnCurrentUserProfile() throws Exception {
        User user = new User();
        user.setId(42L);
        user.setUsername("current_tester");
        user.setUserRole(Role.ROLE_SUPERVISOR);
        user.setEmail("tester@isd.com");

        when(securityFacade.getCurrentUser()).thenReturn(user);

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.username").value("current_tester"))
                .andExpect(jsonPath("$.role").value("ROLE_SUPERVISOR"))
                .andExpect(jsonPath("$.email").value("tester@isd.com"));
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    static class TestConfig {

        @Bean
        public AuthService authService() {
            return mock(AuthService.class);
        }

        @Bean
        public UserService userService() {
            return mock(UserService.class);
        }

        @Bean
        public SecurityFacade securityFacade() {
            return mock(SecurityFacade.class);
        }

        @Bean
        public AuthController authController(
                AuthService authService,
                UserService userService,
                SecurityFacade securityFacade) {
            return new AuthController(authService, userService, securityFacade, false);
        }

        @Bean
        public GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }
}
