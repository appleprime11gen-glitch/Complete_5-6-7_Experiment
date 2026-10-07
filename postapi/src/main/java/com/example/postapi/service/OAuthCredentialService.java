package com.example.postapi.service;

import java.time.Instant;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.example.postapi.crypto.AesGcmEncryptionService;
import com.example.postapi.crypto.EncryptedValue;
import com.example.postapi.dto.OAuthCredentialMetadata;
import com.example.postapi.dto.OAuthCredentialRequest;
import com.example.postapi.model.OAuthCredential;
import com.example.postapi.repository.OAuthCredentialRepository;

@Service
public class OAuthCredentialService {

    private final OAuthCredentialRepository repository;
    private final AesGcmEncryptionService encryptionService;

    public OAuthCredentialService(OAuthCredentialRepository repository, AesGcmEncryptionService encryptionService) {
        this.repository = repository;
        this.encryptionService = encryptionService;
    }

    public OAuthCredentialMetadata create(String ownerId, OAuthCredentialRequest request) {
        OAuthCredential credential = new OAuthCredential();
        credential.setOwnerId(ownerId);
        credential.setProvider(request.getProvider().strip());
        credential.setClientId(request.getClientId().strip());
        credential.setClientSecret(encryptionService.encrypt(request.getClientSecret()));
        credential.setAccessToken(encryptionService.encrypt(request.getAccessToken()));
        credential.setRefreshToken(encryptionService.encrypt(request.getRefreshToken()));
        credential.setCreatedAt(Instant.now());
        return OAuthCredentialMetadata.from(repository.save(credential));
    }

    public List<OAuthCredentialMetadata> listForOwner(String ownerId) {
        return repository.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(OAuthCredentialMetadata::from).toList();
    }

    public void delete(String id, String requesterId, boolean administrator) {
        OAuthCredential credential = repository.findById(id)
                .orElseThrow(() -> new com.example.postapi.exception.ResourceNotFoundException("Credential not found"));
        if (!administrator && !credential.getOwnerId().equals(requesterId)) {
            throw new AccessDeniedException("Credential access denied");
        }
        repository.delete(credential);
    }

    public DecryptedCredentialSecrets decryptForInternalProviderUse(String id, String requesterId,
            boolean administrator) {
        OAuthCredential credential = repository.findById(id)
                .orElseThrow(() -> new com.example.postapi.exception.ResourceNotFoundException("Credential not found"));
        if (!administrator && !credential.getOwnerId().equals(requesterId)) {
            throw new AccessDeniedException("Credential access denied");
        }
        return new DecryptedCredentialSecrets(credential.getProvider(), credential.getClientId(),
                encryptionService.decrypt(credential.getClientSecret()),
                encryptionService.decrypt(credential.getAccessToken()),
                encryptionService.decrypt(credential.getRefreshToken()));
    }

    public record DecryptedCredentialSecrets(String provider, String clientId, String clientSecret,
            String accessToken, String refreshToken) { }
}