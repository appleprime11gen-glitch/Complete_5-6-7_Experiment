package com.example.postapi.controller;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.example.postapi.dto.ApiResponse;
import com.example.postapi.dto.PostRequest;
import com.example.postapi.dto.PaginatedResponse;
import com.example.postapi.model.Post;
import com.example.postapi.service.PostService;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Post>> createPost(@Valid @RequestBody PostRequest request) {
        Post post = postService.createPost(request.getContent());
        ApiResponse<Post> response = new ApiResponse<>("success", "Post created", post);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<Post>>> getAllPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        Page<Post> posts = postService.getAllPosts(page, size, sortBy, direction);
        ApiResponse<PaginatedResponse<Post>> response = new ApiResponse<>("success", "Posts fetched",
                PaginatedResponse.from(posts));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<Post>>> searchPosts(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        Page<Post> posts = postService.searchPosts(keyword, page, size, sortBy, direction);
        ApiResponse<PaginatedResponse<Post>> response = new ApiResponse<>("success", "Posts fetched",
                PaginatedResponse.from(posts));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Post>> getPostById(@PathVariable String id) {
        Post post = postService.getPostById(id);
        ApiResponse<Post> response = new ApiResponse<>("success", "Post fetched", post);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Post>> updatePost(
            @PathVariable String id,
            @Valid @RequestBody PostRequest request) {
        Post post = postService.updatePost(id, request.getContent());
        ApiResponse<Post> response = new ApiResponse<>("success", "Post updated", post);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePost(@PathVariable String id) {
        postService.deletePost(id);
        ApiResponse<Void> response = new ApiResponse<>("success", "Post deleted", null);
        return ResponseEntity.ok(response);
    }
}