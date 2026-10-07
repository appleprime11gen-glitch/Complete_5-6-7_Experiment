package com.example.postapi.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.postapi.dto.ApiResponse;
import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CookieCsrfFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final Set<String> allowedOrigins;

    public CookieCsrfFilter(ObjectMapper objectMapper,
            @Value("${app.security.allowed-origins:http://localhost:8080}")
            String configuredOrigins) {
        this.objectMapper = objectMapper;
        this.allowedOrigins = Arrays.stream(configuredOrigins.split(","))
            .map(String::trim).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean cookieMutation = "POST".equals(request.getMethod())
                && (path.equals("/api/auth/refresh") || path.equals("/api/auth/logout"));
        String origin = request.getHeader("Origin");
        if (cookieMutation && (origin == null || !allowedOrigins.contains(origin)
                || !"1".equals(request.getHeader("X-CSRF-Protection")))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getOutputStream(),
                    new ApiResponse<>("error", "CSRF validation failed", null));
            return;
        }
        filterChain.doFilter(request, response);
    }
}