package com.example.postapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.postapi.model.UserAccount;
import com.example.postapi.repository.UserRepository;

class UserManagementServiceTest {

    @Test
    void bootstrapsOnlyAnUnusedOrAlreadyAdminUsername() {
        UserRepository repository = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        UserManagementService service = new UserManagementService(repository, encoder);
        when(repository.findByUsername("new-admin")).thenReturn(Optional.empty());
        when(encoder.encode("a-unique-admin-password" )).thenReturn("bcrypt-hash");
        when(repository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.bootstrapDevelopmentAdmin("New-Admin", "a-unique-admin-password");

        org.mockito.ArgumentCaptor<UserAccount> saved = org.mockito.ArgumentCaptor.forClass(UserAccount.class);
        verify(repository).save(saved.capture());
        assertEquals("new-admin", saved.getValue().getUsername());
        assertEquals(Set.of("ADMIN"), saved.getValue().getRoles());
        assertEquals("bcrypt-hash", saved.getValue().getPasswordHash());
    }

    @Test
    void doesNotPromoteExistingUserAndIsIdempotentForExistingAdmin() {
        UserRepository repository = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        UserManagementService service = new UserManagementService(repository, encoder);
        when(repository.findByUsername("existing")).thenReturn(Optional.of(
                new UserAccount("existing", "hash", Set.of("USER"))));
        assertThrows(IllegalStateException.class,
                () -> service.bootstrapDevelopmentAdmin("existing", "a-unique-admin-password"));
        verify(repository, never()).save(any(UserAccount.class));

        when(repository.findByUsername("already-admin")).thenReturn(Optional.of(
                new UserAccount("already-admin", "hash", Set.of("ADMIN"))));
        service.bootstrapDevelopmentAdmin("already-admin", "a-unique-admin-password");
        verify(repository, never()).save(any(UserAccount.class));
    }
}