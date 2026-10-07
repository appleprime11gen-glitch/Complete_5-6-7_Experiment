package com.example.postapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.security.SecureRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.example.postapi.dto.LoginRequest;
import com.example.postapi.dto.RegisterRequest;
import com.example.postapi.exception.InvalidRegistrationException;
import com.example.postapi.exception.UsernameAlreadyExistsException;
import com.example.postapi.model.UserAccount;
import com.example.postapi.repository.UserRepository;
import com.example.postapi.security.JwtTokenService;

class AuthServiceTest {

    private UserRepository userRepository;
    private BCryptPasswordEncoder encoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        encoder = new BCryptPasswordEncoder(4);
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        authService = new AuthService(userRepository, encoder,
                new JwtTokenService(java.util.Base64.getEncoder().encodeToString(key), "issuer", "audience", 15),
                mock(RefreshTokenService.class));
    }

    @Test
    void registrationNormalizesUsernameAndOnlyCreatesHashedUserRole() {
        RegisterRequest request = registration(" Alice ", "long-safe-password-123");
        when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount account = invocation.getArgument(0);
            account.setId("user-1");
            return account;
        });

        var view = authService.register(request);

        assertEquals("alice", view.getUsername());
        assertEquals(Set.of("USER"), view.getRoles());
        org.mockito.ArgumentCaptor<UserAccount> captured = org.mockito.ArgumentCaptor.forClass(UserAccount.class);
        org.mockito.Mockito.verify(userRepository).save(captured.capture());
        assertNotEquals(request.getPassword(), captured.getValue().getPasswordHash());
        org.junit.jupiter.api.Assertions.assertTrue(encoder.matches(request.getPassword(),
                captured.getValue().getPasswordHash()));
    }

    @Test
    void publicRegistrationCannotAssignAdminAndDuplicateNamesAreGeneric() {
        RegisterRequest admin = registration("alice", "long-safe-password-123");
        admin.setRoles(Set.of("ADMIN"));
        assertThrows(InvalidRegistrationException.class, () -> authService.register(admin));

        when(userRepository.save(any(UserAccount.class))).thenThrow(new DuplicateKeyException("duplicate"));
        assertThrows(UsernameAlreadyExistsException.class,
                () -> authService.register(registration("alice", "long-safe-password-123")));
    }

    @Test
    void missingAndWrongUsernamesReturnTheSameGenericLoginFailure() {
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(
                new UserAccount("alice", encoder.encode("other-password-123"), Set.of("USER"))));
        assertThrows(BadCredentialsException.class, () -> authService.login(login("missing", "wrong-password")));
        assertThrows(BadCredentialsException.class, () -> authService.login(login("alice", "wrong-password")));
    }

    private RegisterRequest registration(String username, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    private LoginRequest login(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }
}