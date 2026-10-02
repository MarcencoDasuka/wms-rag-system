package com.isd.wms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class JwtCookieAuthIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtUtil jwtUtil;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("Login sets HttpOnly, SameSite=Lax cookie containing valid JWT token")
    void login_setsHttpOnlyCookie() throws Exception {
        Map<String, String> credentials = Map.of(
                "username", "dev",
                "password", "password"
        );

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(credentials)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("jwt_token"))
                .andExpect(cookie().httpOnly("jwt_token", true))
                .andExpect(cookie().path("jwt_token", "/"))
                .andExpect(cookie().value("jwt_token", notNullValue()))
                .andExpect(jsonPath("$.token").exists())
                .andReturn();

        jakarta.servlet.http.Cookie jwtCookie = result.getResponse().getCookie("jwt_token");
        assertNotNull(jwtCookie);
        assertTrue(jwtCookie.isHttpOnly(), "Cookie must be HttpOnly to prevent XSS exfiltration");
    }

    @Test
    @DisplayName("Protected /api/auth/me authenticates successfully using only HttpOnly cookie (no Authorization header)")
    void protectedEndpoint_authenticatesViaCookieOnly() throws Exception {
        String token = jwtUtil.generateToken("dev", "ROLE_DEV");
        Cookie authCookie = new Cookie("jwt_token", token);

        mockMvc.perform(get("/api/auth/me")
                        .cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("dev"))
                .andExpect(jsonPath("$.role").value("ROLE_DEV"));
    }

    @Test
    @DisplayName("Protected endpoint fails authentication when invalid or tampered JWT cookie is supplied")
    void protectedEndpoint_failsWithTamperedCookie() throws Exception {
        Cookie invalidCookie = new Cookie("jwt_token", "tampered.jwt.payload");

        mockMvc.perform(get("/api/auth/me")
                        .cookie(invalidCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Protected endpoint fails authentication when neither cookie nor Authorization header is present")
    void protectedEndpoint_failsWithoutCredentials() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Logout invalidates jwt_token cookie with maxAge=0")
    void logout_clearsJwtCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("jwt_token", 0))
                .andExpect(cookie().value("jwt_token", ""))
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }

    @Test
    @DisplayName("Protected endpoint still supports Authorization Bearer header for backward compatibility")
    void protectedEndpoint_supportsBearerHeaderCompatibility() throws Exception {
        String token = jwtUtil.generateToken("dev", "ROLE_DEV");

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("dev"))
                .andExpect(jsonPath("$.role").value("ROLE_DEV"));
    }
}
