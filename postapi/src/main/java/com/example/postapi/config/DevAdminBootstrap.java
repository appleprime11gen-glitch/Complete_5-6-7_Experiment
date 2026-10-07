package com.example.postapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.example.postapi.service.UserManagementService;

@Configuration
@Profile("dev")
@ConditionalOnProperty(name = "app.security.dev-admin-bootstrap-enabled", havingValue = "true")
public class DevAdminBootstrap {

    @Bean
    ApplicationRunner bootstrapAdmin(UserManagementService userManagementService,
            @Value("${DEV_ADMIN_USERNAME:}") String username,
            @Value("${DEV_ADMIN_PASSWORD:}") String password) {
        return args -> userManagementService.bootstrapDevelopmentAdmin(username, password);
    }
}