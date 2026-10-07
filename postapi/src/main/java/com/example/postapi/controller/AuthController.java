package com.example.postapi.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.postapi.dto.ApiResponse;
import com.example.postapi.dto.LoginRequest;
import com.example.postapi.dto.RegisterRequest;
import com.example.postapi.dto.TokenResponse;
import com.example.postapi.dto.UserView;
import com.example.postapi.security.AuthenticatedUser;
import com.example.postapi.service.AuthService;
import com.example.postapi.service.AuthService.AuthSession;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String REFRESH_COOKIE = "POST_REFRESH";

    private final AuthService authService;
    private final boolean secureCookie;
    private final String cookieSameSite;

    public AuthController(AuthService authService,
            @Value("${app.auth.cookie-secure:true}") boolean secureCookie,
            @Value("${app.auth.cookie-same-site:Strict}") String cookieSameSite) {
        this.authService = authService;
        if (!Set.of("Strict", "Lax", "None").contains(cookieSameSite)
                || ("None".equals(cookieSameSite) && !secureCookie)) {
            throw new IllegalStateException("Refresh cookie SameSite must be Strict, Lax, or Secure None");
        }
        this.secureCookie = secureCookie;
        this.cookieSameSite = cookieSameSite;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserView>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>("success", "Account created", authService.register(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return tokenResponse(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken) {
        return tokenResponse(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken) {
        authService.logout(refreshToken);
        ResponseCookie clearCookie = cookie("", Duration.ZERO);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .body(new ApiResponse<>("success", "Logged out", null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserView>> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(new ApiResponse<>("success", "Current user", UserView.from(user)));
    }

    private ResponseEntity<ApiResponse<TokenResponse>> tokenResponse(AuthSession session) {
        long remainingSeconds = Math.max(0, Duration.between(Instant.now(), session.refreshExpiresAt()).toSeconds());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(session.refreshToken(), Duration.ofSeconds(remainingSeconds)).toString())
                .body(new ApiResponse<>("success", "Authenticated", session.response()));
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(cookieSameSite)
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
    }
}