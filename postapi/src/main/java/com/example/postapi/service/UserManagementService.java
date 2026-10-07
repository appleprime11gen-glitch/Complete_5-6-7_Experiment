package com.example.postapi.service;

import java.util.List;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.postapi.dto.UserView;
import com.example.postapi.model.UserAccount;
import com.example.postapi.repository.UserRepository;

@Service
public class UserManagementService {

    private static final Set<String> ALLOWED_ROLES = Set.of("USER", "ADMIN");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserManagementService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserView> listUsers() {
        return userRepository.findAll().stream().map(UserView::from).toList();
    }

    public UserView updateRoles(String id, Set<String> roles) {
        if (roles == null || roles.isEmpty() || !ALLOWED_ROLES.containsAll(roles)) {
            throw new IllegalArgumentException("Roles must contain USER, ADMIN, or both");
        }
        UserAccount user = userRepository.findById(id)
                .orElseThrow(() -> new com.example.postapi.exception.ResourceNotFoundException("User not found"));
        if (user.getRoles().contains("ADMIN") && !roles.contains("ADMIN")
                && userRepository.countByRolesContaining("ADMIN") <= 1) {
            throw new IllegalArgumentException("The last administrator cannot be demoted");
        }
        user.setRoles(roles);
        return UserView.from(userRepository.save(user));
    }

    public void bootstrapDevelopmentAdmin(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.length() < 12
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("DEV_ADMIN_USERNAME and a 12-72 byte DEV_ADMIN_PASSWORD are required");
        }
        String normalized = AuthService.normalize(username);
        UserAccount existing = userRepository.findByUsername(normalized).orElse(null);
        if (existing != null) {
            if (existing.getRoles().contains("ADMIN")) {
                return;
            }
            throw new IllegalStateException("DEV_ADMIN_USERNAME already belongs to a non-admin; choose another username");
        }
        userRepository.save(new UserAccount(normalized, passwordEncoder.encode(password), Set.of("ADMIN")));
    }
}