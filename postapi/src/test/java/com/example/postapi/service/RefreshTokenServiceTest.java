package com.example.postapi.service;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.example.postapi.exception.InvalidRefreshTokenException;
import com.example.postapi.model.RefreshTokenFamily;
import com.example.postapi.repository.RefreshTokenFamilyRepository;

class RefreshTokenServiceTest {

    @Test
    void atomicallyRotatesRefreshTokensAndRejectsReuse() {
        RefreshTokenFamilyRepository repository = mock(RefreshTokenFamilyRepository.class);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class, RETURNS_DEEP_STUBS);
        RefreshTokenService service = new RefreshTokenService(repository, mongoTemplate, 7);
        RefreshTokenFamily family = family("family-1", "user-1", "old-hash");
        when(repository.findByCurrentTokenHash(anyString())).thenReturn(Optional.of(family));
        RefreshTokenFamily rotated = family("family-1", "user-1", "new-hash");
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), org.mockito.ArgumentMatchers.eq(RefreshTokenFamily.class)))
                .thenReturn(rotated);

        RefreshTokenService.RotatedRefreshToken result = service.rotate("raw-old-token");

        assertNotEquals("raw-old-token", result.rawToken());
        org.mockito.ArgumentCaptor<Query> query = org.mockito.ArgumentCaptor.forClass(Query.class);
        org.mockito.Mockito.verify(mongoTemplate).findAndModify(query.capture(), any(Update.class),
                any(FindAndModifyOptions.class), org.mockito.ArgumentMatchers.eq(RefreshTokenFamily.class));
        org.mockito.Mockito.verify(repository).findByCurrentTokenHash(org.mockito.ArgumentMatchers.argThat(
                hash -> hash != null && !hash.equals("raw-old-token")));

        when(repository.findByCurrentTokenHash(anyString())).thenReturn(Optional.empty());
        when(repository.findByPreviousTokenHashesContaining(anyString())).thenReturn(Optional.of(rotated));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), org.mockito.ArgumentMatchers.eq(RefreshTokenFamily.class)))
                .thenReturn(null);
        assertThrows(InvalidRefreshTokenException.class, () -> service.rotate("raw-old-token"));
    }

    @Test
    void rejectsExpiredRefreshFamilyBeforeRotation() {
        RefreshTokenFamilyRepository repository = mock(RefreshTokenFamilyRepository.class);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class, RETURNS_DEEP_STUBS);
        RefreshTokenService service = new RefreshTokenService(repository, mongoTemplate, 7);
        when(repository.findByCurrentTokenHash(anyString())).thenReturn(Optional.of(
                new RefreshTokenFamily("user-1", "hash", Instant.now().minusSeconds(100),
                        Instant.now().minusSeconds(1))));

        assertThrows(InvalidRefreshTokenException.class, () -> service.rotate("opaque-expired-token"));
        org.mockito.Mockito.verifyNoInteractions(mongoTemplate);
    }

    private RefreshTokenFamily family(String id, String userId, String hash) {
        RefreshTokenFamily family = new RefreshTokenFamily(userId, hash, Instant.now(), Instant.now().plusSeconds(3600));
        family.setId(id);
        return family;
    }
}