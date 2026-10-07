package com.example.postapi.security;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AuthenticatedUser implements UserDetails {

    private final String id;
    private final String username;
    private final Set<String> roles;

    public AuthenticatedUser(String id, String username, Set<String> roles) {
        this.id = id;
        this.username = username;
        this.roles = Set.copyOf(roles);
    }

    public String getId() { return id; }
    public Set<String> getRoles() { return roles; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public String getPassword() { return ""; }

    @Override
    public String getUsername() { return username; }
}