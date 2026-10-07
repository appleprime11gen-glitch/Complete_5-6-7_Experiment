package com.example.postapi.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.example.postapi.exception.InvalidRefreshTokenException;
import com.example.postapi.model.RefreshTokenFamily;
import com.example.postapi.repository.RefreshTokenFamilyRepository;

@Service
public class RefreshTokenService {

    private final RefreshTokenFamilyRepository repository;
    private final MongoTemplate mongoTemplate;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long lifetimeDays;

    public RefreshTokenService(RefreshTokenFamilyRepository repository, MongoTemplate mongoTemplate,
            @Value("${app.auth.refresh-days:7}") long lifetimeDays) {
        if (lifetimeDays < 1) {
            throw new IllegalStateException("Refresh-token lifetime must be at least one day");
        }
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
        this.lifetimeDays = lifetimeDays;
    }

    public IssuedRefreshToken create(String userId) {
        Instant now = Instant.now();
        String rawToken = createRawToken();
        Instant expiresAt = now.plus(lifetimeDays, ChronoUnit.DAYS);
        repository.save(new RefreshTokenFamily(userId, hash(rawToken), now, expiresAt));
        return new IssuedRefreshToken(rawToken, expiresAt);
    }

    public RotatedRefreshToken rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        String oldHash = hash(rawToken);
        RefreshTokenFamily family = repository.findByCurrentTokenHash(oldHash).orElse(null);
        if (family == null) {
            repository.findByPreviousTokenHashesContaining(oldHash).ifPresent(this::revokeFamily);
            throw new InvalidRefreshTokenException();
        }

        Instant now = Instant.now();
        if (family.getRevokedAt() != null || !family.getExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException();
        }

        String newRawToken = createRawToken();
        String newHash = hash(newRawToken);
        Query query = Query.query(Criteria.where("_id").is(family.getId())
                .and("currentTokenHash").is(oldHash)
                .and("expiresAt").gt(now)
                .and("revokedAt").is(null));
        Update update = new Update().set("currentTokenHash", newHash).push("previousTokenHashes", oldHash);
        RefreshTokenFamily rotated = mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), RefreshTokenFamily.class);
        if (rotated == null) {
            repository.findByPreviousTokenHashesContaining(oldHash).ifPresent(this::revokeFamily);
            throw new InvalidRefreshTokenException();
        }
        return new RotatedRefreshToken(rotated.getUserId(), newRawToken, rotated.getExpiresAt());
    }

    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        String tokenHash = hash(rawToken);
        repository.findByCurrentTokenHash(tokenHash)
                .or(() -> repository.findByPreviousTokenHashesContaining(tokenHash))
                .ifPresent(this::revokeFamily);
    }

    private void revokeFamily(RefreshTokenFamily family) {
        mongoTemplate.update(RefreshTokenFamily.class)
                .matching(Query.query(Criteria.where("_id").is(family.getId()).and("revokedAt").is(null)))
                .apply(Update.update("revokedAt", Instant.now()))
                .first();
    }

    private String createRawToken() {
        byte[] token = new byte[32];
        secureRandom.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record IssuedRefreshToken(String rawToken, Instant expiresAt) { }
    public record RotatedRefreshToken(String userId, String rawToken, Instant expiresAt) { }
}