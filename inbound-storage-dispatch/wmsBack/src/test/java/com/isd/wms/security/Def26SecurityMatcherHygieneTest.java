package com.isd.wms.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Def26SecurityMatcherHygieneTest {

    @Test
    @DisplayName("DEF-26: SecurityConfig does not contain legacy or dead /api/operator matcher")
    void securityConfig_DoesNotContainOperatorMatcher() throws IOException {
        Path securityConfigPath = Paths.get("src/main/java/com/isd/wms/security/SecurityConfig.java");
        if (!Files.exists(securityConfigPath)) {
            // Relative fallback when executed from root or subproject directory
            securityConfigPath = Paths.get("inbound-storage-dispatch/wmsBack/src/main/java/com/isd/wms/security/SecurityConfig.java");
        }

        assertThat(Files.exists(securityConfigPath)).isTrue();
        String content = Files.readString(securityConfigPath);

        assertThat(content)
            .withFailMessage("SecurityConfig must not contain dead /api/operator matcher")
            .doesNotContain("/api/operator/**");

        assertThat(content)
            .withFailMessage("SecurityConfig must restrict /api/supervisor/** to supervisor and dev roles")
            .contains("/api/supervisor/**");

        assertThat(content)
            .withFailMessage("SecurityConfig must end with anyRequest().authenticated()")
            .contains(".anyRequest().authenticated()");
    }

    @Test
    @DisplayName("DEF-26: No controller exposes dead /api/operator endpoints")
    void controllers_DoNotExposeDeadOperatorEndpoints() {
        // Inspect known controllers in WMS application
        List<Class<?>> controllers = List.of(
            com.isd.wms.controller.AiChatController.class,
            com.isd.wms.controller.AllocationController.class,
            com.isd.wms.controller.AuthController.class,
            com.isd.wms.controller.CategoryController.class,
            com.isd.wms.controller.InventoryController.class,
            com.isd.wms.controller.LocationController.class,
            com.isd.wms.controller.OrderController.class,
            com.isd.wms.controller.OrderLineController.class,
            com.isd.wms.controller.ProductController.class,
            com.isd.wms.controller.ReplenishmentController.class,
            com.isd.wms.controller.SupervisorDashboardController.class,
            com.isd.wms.controller.UserController.class
        );

        for (Class<?> controller : controllers) {
            assertThat(controller.isAnnotationPresent(RestController.class)).isTrue();
            RequestMapping mapping = controller.getAnnotation(RequestMapping.class);
            if (mapping != null) {
                for (String path : mapping.value()) {
                    assertThat(path)
                        .withFailMessage("Controller %s must not map to /api/operator", controller.getSimpleName())
                        .doesNotStartWith("/api/operator");
                }
            }
        }
    }
}
