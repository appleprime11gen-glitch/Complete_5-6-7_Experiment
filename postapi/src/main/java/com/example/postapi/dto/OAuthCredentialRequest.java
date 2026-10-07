package com.example.postapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class OAuthCredentialRequest {

    @NotBlank
    @Size(max = 80)
    @Pattern(regexp = "[A-Za-z0-9._-]+")
    private String provider;

    @NotBlank
    @Size(max = 256)
    private String clientId;

    @NotBlank
    @Size(max = 4096)
    private String clientSecret;

    @Size(max = 8192)
    private String accessToken;

    @Size(max = 8192)
    private String refreshToken;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}