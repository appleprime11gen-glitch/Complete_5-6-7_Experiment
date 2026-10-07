package com.example.postapi.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.example.postapi.repository.PostRepository;

@Component("postAuthorization")
public class PostAuthorization {

    private final PostRepository postRepository;

    public PostAuthorization(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    public boolean isOwner(String postId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return false;
        }
        return postRepository.existsByIdAndOwnerId(postId, user.getId());
    }
}