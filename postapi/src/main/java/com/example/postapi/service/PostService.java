package com.example.postapi.service;

import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.example.postapi.exception.ResourceNotFoundException;
import com.example.postapi.model.Post;
import com.example.postapi.repository.PostRepository;
import com.example.postapi.security.AuthenticatedUser;

@Service
public class PostService {

    private static final Set<String> SORT_FIELDS = Set.of("id", "content", "createdAt");

    private final PostRepository postRepository;

    public PostService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @CacheEvict(cacheNames = {"postById", "postPages", "postSearchPages"}, allEntries = true)
    public Post createPost(String content) {
        Post post = new Post(content);
        AuthenticatedUser user = (AuthenticatedUser) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        post.setOwnerId(user.getId());
        return postRepository.save(post);
    }

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Cacheable(cacheNames = "postPages",
            key = "T(java.util.List).of(#page, #size, #sortBy, #direction)")
    public Page<Post> getAllPosts(int page, int size, String sortBy, String direction) {
        return postRepository.findAll(createPageable(page, size, sortBy, direction));
    }

        @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
        @Cacheable(cacheNames = "postSearchPages",
            key = "T(java.util.List).of(#keyword == null ? '' : #keyword, #page, #size, #sortBy, #direction)")
        public Page<Post> searchPosts(String keyword, int page, int size, String sortBy, String direction) {
        Pageable pageable = createPageable(page, size, sortBy, direction);
        if (keyword == null || keyword.isBlank()) {
            return postRepository.findAll(pageable);
        }
        return postRepository.findByContentContainingIgnoreCase(keyword.trim(), pageable);
    }

    private Pageable createPageable(int page, int size, String sortBy, String direction) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
        }
        if (sortBy == null || !SORT_FIELDS.contains(sortBy)) {
            throw new IllegalArgumentException("sortBy must be one of: id, content, createdAt");
        }
        if (direction == null || !(direction.equalsIgnoreCase("asc") || direction.equalsIgnoreCase("desc"))) {
            throw new IllegalArgumentException("direction must be asc or desc");
        }

        Sort.Direction sortDirection = Sort.Direction.fromString(direction);
        Sort sort = Sort.by(sortDirection, sortBy);
        if (!sortBy.equals("id")) {
            sort = sort.and(Sort.by(sortDirection, "id"));
        }
        return PageRequest.of(page, size, sort);
    }

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Cacheable(cacheNames = "postById", key = "#id")
    public Post getPostById(String id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found with id: " + id));
    }

    @PreAuthorize("hasRole('ADMIN') or @postAuthorization.isOwner(#id, authentication)")
    @CacheEvict(cacheNames = {"postById", "postPages", "postSearchPages"}, allEntries = true)
    public Post updatePost(String id, String content) {
        Post post = getPostById(id);
        post.setContent(content);
        return postRepository.save(post);
    }

    @PreAuthorize("hasRole('ADMIN') or @postAuthorization.isOwner(#id, authentication)")
    @CacheEvict(cacheNames = {"postById", "postPages", "postSearchPages"}, allEntries = true)
    public void deletePost(String id) {
        getPostById(id);
        postRepository.deleteById(id);
    }
}