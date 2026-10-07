package com.example.postapi.dto;

import java.util.Set;

import com.example.postapi.model.UserAccount;
import com.example.postapi.security.AuthenticatedUser;

public class UserView {

    private final String id;
    private final String username;
    private final Set<String> roles;

    public UserView(String id, String username, Set<String> roles) {
        this.id = id;
        this.username = username;
        this.roles = Set.copyOf(roles);
    }

    public static UserView from(UserAccount user) {
        return new UserView(user.getId(), user.getUsername(), user.getRoles());
    }

    public static UserView from(AuthenticatedUser user) {
        return new UserView(user.getId(), user.getUsername(), user.getRoles());
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public Set<String> getRoles() { return roles; }
}