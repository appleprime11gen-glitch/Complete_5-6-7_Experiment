package com.example.postapi.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Set;
import java.security.SecureRandom;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtTokenServiceTest {

    private byte[] keyBytes;
    private JwtTokenService tokenService;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        key = Keys.hmacShaKeyFor(keyBytes);
        tokenService = new JwtTokenService(Base64.getEncoder().encodeToString(keyBytes),
                "postapi-test", "postapi-client-test", 15);
    }

    @Test
    void issuesAndValidatesTheConfiguredAccessToken() {
        AuthenticatedUser user = new AuthenticatedUser("user-1", "alice", Set.of("USER"));

        AuthenticatedUser validated = tokenService.validate(tokenService.issue(user));

        assertEquals("user-1", validated.getId());
        assertEquals("alice", validated.getUsername());
        assertEquals(Set.of("USER"), validated.getRoles());
    }

    @Test
    void rejectsMalformedExpiredWrongIssuerAudienceAndAlgorithmTokens() {
        assertThrows(JwtException.class, () -> tokenService.validate("not-a-jwt"));
        assertThrows(JwtException.class, () -> tokenService.validate(buildToken(key,
                "postapi-test", "postapi-client-test", Instant.now().minusSeconds(30), Jwts.SIG.HS256)));
        assertThrows(JwtException.class, () -> tokenService.validate(buildToken(key,
                "other-issuer", "postapi-client-test", Instant.now().plusSeconds(300), Jwts.SIG.HS256)));
        assertThrows(JwtException.class, () -> tokenService.validate(buildToken(key,
                "postapi-test", "other-audience", Instant.now().plusSeconds(300), Jwts.SIG.HS256)));
        assertThrows(JwtException.class, () -> tokenService.validate(buildToken(key,
                "postapi-test", "postapi-client-test", Instant.now().plusSeconds(300), Jwts.SIG.HS384)));

        byte[] wrongKeyBytes = keyBytes.clone();
        wrongKeyBytes[0] ^= 1;
        assertThrows(JwtException.class, () -> tokenService.validate(buildToken(Keys.hmacShaKeyFor(wrongKeyBytes),
                "postapi-test", "postapi-client-test", Instant.now().plusSeconds(300), Jwts.SIG.HS256)));
    }

    private String buildToken(SecretKey signingKey, String issuer, String audience,
            Instant expiration, io.jsonwebtoken.security.MacAlgorithm algorithm) {
        Instant now = Instant.now();
        return Jwts.builder().subject("user-1").claim("username", "alice").claim("roles", Set.of("USER"))
                .issuer(issuer).audience().add(audience).and()
                .issuedAt(Date.from(now)).expiration(Date.from(expiration))
                .signWith(signingKey, algorithm).compact();
    }
}