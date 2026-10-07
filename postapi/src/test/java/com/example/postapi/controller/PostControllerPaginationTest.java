package com.example.postapi.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.postapi.exception.GlobalExceptionHandler;
import com.example.postapi.model.Post;
import com.example.postapi.service.PostService;

class PostControllerPaginationTest {

    private PostService postService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        postService = mock(PostService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PostController(postService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsPaginationMetadataUsingDefaults() throws Exception {
        Post post = new Post("one");
        post.setId("post-1");
        when(postService.getAllPosts(0, 10, "createdAt", "desc"))
                .thenReturn(new PageImpl<>(List.of(post), PageRequest.of(0, 10), 11));

        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value("post-1"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(11))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.last").value(false));

        verify(postService).getAllPosts(0, 10, "createdAt", "desc");
    }

    @Test
    void returnsBadRequestForInvalidQueryValues() throws Exception {
        when(postService.getAllPosts(-1, 10, "createdAt", "desc"))
                .thenThrow(new IllegalArgumentException("invalid page"));

        mockMvc.perform(get("/api/posts").queryParam("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Invalid pagination or sorting parameters"));

        mockMvc.perform(get("/api/posts").queryParam("page", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Invalid pagination or sorting parameters"));
    }

    @Test
    void sendsSearchPagingAndSortInputsToService() throws Exception {
        when(postService.searchPosts("spring", 2, 25, "content", "asc"))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 25), 50));

        mockMvc.perform(get("/api/posts/search")
                        .queryParam("keyword", "spring")
                        .queryParam("page", "2")
                        .queryParam("size", "25")
                        .queryParam("sortBy", "content")
                        .queryParam("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2));

        verify(postService).searchPosts("spring", 2, 25, "content", "asc");
    }
}