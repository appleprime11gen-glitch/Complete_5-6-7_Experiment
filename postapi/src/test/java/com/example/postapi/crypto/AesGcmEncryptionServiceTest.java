package com.example.postapi.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.Optional;
import java.security.SecureRandom;

import org.junit.jupiter.api.Test;

import com.example.postapi.dto.OAuthCredentialRequest;
import com.example.postapi.dto.OAuthCredentialMetadata;
import com.example.postapi.model.OAuthCredential;
import com.example.postapi.repository.OAuthCredentialRepository;
import com.example.postapi.service.OAuthCredentialService;

class AesGcmEncryptionServiceTest {

    private final byte[] keyBytes = randomKey();
    private final AesGcmEncryptionService encryptionService = new AesGcmEncryptionService(
            Base64.getEncoder().encodeToString(keyBytes), "test-v1");

    private static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    @Test
    void encryptsAndDecryptsWithFreshNoncesAndRejectsTampering() {
        EncryptedValue first = encryptionService.encrypt("client-secret-value");
        EncryptedValue second = encryptionService.encrypt("client-secret-value");

        assertEquals("client-secret-value", encryptionService.decrypt(first));
        assertNotEquals(first.getNonce(), second.getNonce());
        assertEquals("test-v1", first.getKeyVersion());

        first.setCiphertext(first.getCiphertext().startsWith("A")
                ? "B" + first.getCiphertext().substring(1)
                : "A" + first.getCiphertext().substring(1));
        assertThrows(IllegalStateException.class, () -> encryptionService.decrypt(first));
    }

    @Test
    void persistsOnlyEncryptedSecretsAndReturnsOnlyMetadata() {
        OAuthCredentialRepository repository = mock(OAuthCredentialRepository.class);
        when(repository.save(any(OAuthCredential.class))).thenAnswer(invocation -> {
            OAuthCredential saved = invocation.getArgument(0);
            saved.setId("credential-1");
            return saved;
        });
        OAuthCredentialService service = new OAuthCredentialService(repository, encryptionService);
        OAuthCredentialRequest request = new OAuthCredentialRequest();
        request.setProvider("example-provider");
        request.setClientId("public-client-id");
        request.setClientSecret("private-client-secret");
        request.setAccessToken("private-access-token");
        request.setRefreshToken("private-refresh-token");

        OAuthCredentialMetadata metadata = service.create("owner-1", request);

        assertEquals("example-provider", metadata.getProvider());
        assertEquals("public-client-id", metadata.getClientId());
        org.mockito.ArgumentCaptor<OAuthCredential> captured = org.mockito.ArgumentCaptor.forClass(OAuthCredential.class);
        org.mockito.Mockito.verify(repository).save(captured.capture());
        OAuthCredential stored = captured.getValue();
        when(repository.findById("credential-1")).thenReturn(Optional.of(stored));
        assertNotEquals(request.getClientSecret(), stored.getClientSecret().getCiphertext());
        assertEquals("private-client-secret", encryptionService.decrypt(stored.getClientSecret()));
        assertEquals("private-access-token", encryptionService.decrypt(stored.getAccessToken()));
        assertEquals("private-refresh-token", encryptionService.decrypt(stored.getRefreshToken()));
        OAuthCredentialService.DecryptedCredentialSecrets internal =
            service.decryptForInternalProviderUse("credential-1", "owner-1", false);
        assertEquals("private-client-secret", internal.clientSecret());
        assertEquals("private-access-token", internal.accessToken());
    }
}