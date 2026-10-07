package com.example.postapi.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtTokenService {

    private final SecretKey signingKey;
    private final String issuer;
    private final String audience;
    private final Duration accessLifetime;

    public JwtTokenService(
            @Value("${app.jwt.secret-base64:}") String secretBase64,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.audience}") String audience,
            @Value("${app.jwt.access-minutes:15}") long accessMinutes) {
        if (secretBase64.isBlank()) {
            throw new IllegalStateException("JWT_SECRET_BASE64 must be configured before starting the application");
        }
        byte[] secret;
        try {
            secret = Base64.getDecoder().decode(secretBase64);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT_SECRET_BASE64 must contain valid Base64", exception);
        }
        if (secret.length < 32) {
            throw new IllegalStateException("JWT_SECRET_BASE64 must decode to at least 32 bytes for HS256");
        }
        if (accessMinutes < 1) {
            throw new IllegalStateException("JWT access lifetime must be at least one minute");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret);
        this.issuer = issuer;
        this.audience = audience;
        this.accessLifetime = Duration.ofMinutes(accessMinutes);
    }

    public String issue(AuthenticatedUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId())
                .claim("username", user.getUsername())
                .claim("roles", user.getRoles())
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessLifetime)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public AuthenticatedUser validate(String token) {
        Jws<Claims> signed = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token);
        if (!Jwts.SIG.HS256.getId().equals(signed.getHeader().getAlgorithm())) {
            throw new JwtException("Unsupported signing algorithm");
        }
        Claims claims = signed.getPayload();
        if (claims.getExpiration() == null) {
            throw new JwtException("Token expiration is required");
        }
        if (claims.getAudience() == null || !claims.getAudience().contains(audience)) {
            throw new JwtException("Invalid token audience");
        }
        Object rolesClaim = claims.get("roles");
        if (!(rolesClaim instanceof List<?> roles) || roles.isEmpty()
                || roles.stream().anyMatch(role -> !(role instanceof String value)
                        || !(value.equals("USER") || value.equals("ADMIN")))) {
            throw new JwtException("Invalid token roles");
        }
        String userId = claims.getSubject();
        String username = claims.get("username", String.class);
        if (userId == null || username == null) {
            throw new JwtException("Invalid token subject");
        }
        return new AuthenticatedUser(userId, username, roles.stream().map(String.class::cast).collect(
                java.util.stream.Collectors.toUnmodifiableSet()));
    }

    public long accessLifetimeSeconds() {
        return accessLifetime.toSeconds();
    }
}