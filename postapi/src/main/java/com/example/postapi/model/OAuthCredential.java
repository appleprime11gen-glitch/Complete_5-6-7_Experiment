package com.example.postapi.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.example.postapi.crypto.EncryptedValue;

@Document(collection = "oauth_credentials")
@CompoundIndex(name = "oauth_credentials_owner", def = "{'ownerId': 1, 'createdAt': -1}")
public class OAuthCredential {

    @Id
    private String id;
    private String ownerId;
    private String provider;
    private String clientId;
    private EncryptedValue clientSecret;
    private EncryptedValue accessToken;
    private EncryptedValue refreshToken;
    private Instant createdAt;

    public OAuthCredential() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public EncryptedValue getClientSecret() { return clientSecret; }
    public void setClientSecret(EncryptedValue clientSecret) { this.clientSecret = clientSecret; }
    public EncryptedValue getAccessToken() { return accessToken; }
    public void setAccessToken(EncryptedValue accessToken) { this.accessToken = accessToken; }
    public EncryptedValue getRefreshToken() { return refreshToken; }
    public void setRefreshToken(EncryptedValue refreshToken) { this.refreshToken = refreshToken; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}