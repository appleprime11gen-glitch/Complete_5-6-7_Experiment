package com.example.postapi.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.example.postapi.repository.OAuthCredentialRepository;

@Component("oauthCredentialAuthorization")
public class OAuthCredentialAuthorization {

    private final OAuthCredentialRepository repository;

    public OAuthCredentialAuthorization(OAuthCredentialRepository repository) {
        this.repository = repository;
    }

    public boolean isOwner(String credentialId, Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
                && repository.existsByIdAndOwnerId(credentialId, user.getId());
    }
}