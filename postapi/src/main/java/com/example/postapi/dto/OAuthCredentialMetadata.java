package com.example.postapi.dto;

import java.time.Instant;

import com.example.postapi.model.OAuthCredential;

public class OAuthCredentialMetadata {

    private final String id;
    private final String provider;
    private final String clientId;
    private final Instant createdAt;

    public OAuthCredentialMetadata(String id, String provider, String clientId, Instant createdAt) {
        this.id = id;
        this.provider = provider;
        this.clientId = clientId;
        this.createdAt = createdAt;
    }

    public static OAuthCredentialMetadata from(OAuthCredential credential) {
        return new OAuthCredentialMetadata(credential.getId(), credential.getProvider(),
                credential.getClientId(), credential.getCreatedAt());
    }

    public String getId() { return id; }
    public String getProvider() { return provider; }
    public String getClientId() { return clientId; }
    public Instant getCreatedAt() { return createdAt; }
}