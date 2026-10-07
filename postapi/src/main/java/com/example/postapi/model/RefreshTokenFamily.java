package com.example.postapi.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "refresh_token_families")
public class RefreshTokenFamily {

    @Id
    private String id;
    private String userId;
    @Indexed(unique = true, name = "refresh_current_hash_unique")
    private String currentTokenHash;
    private List<String> previousTokenHashes = new ArrayList<>();
    private Instant createdAt;
    private Instant expiresAt;
    private Instant revokedAt;

    public RefreshTokenFamily() {
    }

    public RefreshTokenFamily(String userId, String currentTokenHash, Instant createdAt, Instant expiresAt) {
        this.userId = userId;
        this.currentTokenHash = currentTokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getCurrentTokenHash() { return currentTokenHash; }
    public void setCurrentTokenHash(String currentTokenHash) { this.currentTokenHash = currentTokenHash; }
    public List<String> getPreviousTokenHashes() { return previousTokenHashes; }
    public void setPreviousTokenHashes(List<String> previousTokenHashes) { this.previousTokenHashes = previousTokenHashes; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
}