package com.example.postapi.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.postapi.dto.LoginRequest;
import com.example.postapi.dto.RegisterRequest;
import com.example.postapi.dto.TokenResponse;
import com.example.postapi.dto.UserView;
import com.example.postapi.exception.InvalidRefreshTokenException;
import com.example.postapi.exception.InvalidRegistrationException;
import com.example.postapi.exception.UsernameAlreadyExistsException;
import com.example.postapi.model.UserAccount;
import com.example.postapi.repository.UserRepository;
import com.example.postapi.security.AuthenticatedUser;
import com.example.postapi.security.JwtTokenService;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        byte[] random = new byte[24];
        new SecureRandom().nextBytes(random);
        this.dummyPasswordHash = passwordEncoder.encode(UUID.nameUUIDFromBytes(random).toString());
    }

    public UserView register(RegisterRequest request) {
        if (request.getRoles() != null && !request.getRoles().equals(Set.of("USER"))) {
            throw new InvalidRegistrationException("Public registration only permits the USER role");
        }
        if (request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidRegistrationException("Password must be no more than 72 UTF-8 bytes");
        }
        String username = normalize(request.getUsername());
        UserAccount user = new UserAccount(username, passwordEncoder.encode(request.getPassword()), Set.of("USER"));
        try {
            return UserView.from(userRepository.save(user));
        } catch (DuplicateKeyException exception) {
            throw new UsernameAlreadyExistsException();
        }
    }

    public AuthSession login(LoginRequest request) {
        String username = normalize(request.getUsername());
        UserAccount user = userRepository.findByUsername(username).orElse(null);
        String passwordHash = user == null ? dummyPasswordHash : user.getPasswordHash();
        boolean passwordTooLong = request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72;
        boolean passwordMatches = !passwordTooLong && passwordEncoder.matches(request.getPassword(), passwordHash);
        if (user == null || !passwordMatches) {
            throw new BadCredentialsException("Invalid username or password");
        }
        return createSession(user);
    }

    public AuthSession refresh(String rawRefreshToken) {
        RefreshTokenService.RotatedRefreshToken rotated = refreshTokenService.rotate(rawRefreshToken);
        UserAccount user = userRepository.findById(rotated.userId()).orElse(null);
        if (user == null) {
            refreshTokenService.revoke(rotated.rawToken());
            throw new InvalidRefreshTokenException();
        }
        AuthenticatedUser principal = principal(user);
        return new AuthSession(new TokenResponse(jwtTokenService.issue(principal),
                jwtTokenService.accessLifetimeSeconds(), UserView.from(principal)),
                rotated.rawToken(), rotated.expiresAt());
    }

    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    public static String normalize(String username) {
        return username.strip().toLowerCase(java.util.Locale.ROOT);
    }

    private AuthSession createSession(UserAccount user) {
        AuthenticatedUser principal = principal(user);
        RefreshTokenService.IssuedRefreshToken refresh = refreshTokenService.create(user.getId());
        return new AuthSession(new TokenResponse(jwtTokenService.issue(principal),
                jwtTokenService.accessLifetimeSeconds(), UserView.from(principal)),
                refresh.rawToken(), refresh.expiresAt());
    }

    private AuthenticatedUser principal(UserAccount user) {
        return new AuthenticatedUser(user.getId(), user.getUsername(), user.getRoles());
    }

    public record AuthSession(TokenResponse response, String refreshToken,
            java.time.Instant refreshExpiresAt) { }
}