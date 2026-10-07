package com.example.postapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.example.postapi.model.Post;
import com.example.postapi.repository.PostRepository;

@ExtendWith(MockitoExtension.class)
class PostServicePaginationTest {

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private PostService postService;

    private Post post;

    @BeforeEach
    void setUp() {
        post = new Post("spring data pagination");
        post.setId("post-1");
    }

    @Test
    void paginatesAndAddsStableIdSort() {
        when(postRepository.findAll(any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(post), invocation.getArgument(0), 21));

        Page<Post> result = postService.getAllPosts(1, 10, "createdAt", "desc");

        assertEquals(21, result.getTotalElements());
        assertEquals(1, result.getNumber());
        assertEquals(10, result.getSize());
        assertEquals("id", result.getSort().getOrderFor("id").getProperty());
        assertEquals("createdAt", result.getSort().getOrderFor("createdAt").getProperty());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).findAll(pageable.capture());
        assertEquals(10, pageable.getValue().getOffset());
    }

    @Test
    void paginatesKeywordSearchInRepository() {
        when(postRepository.findByContentContainingIgnoreCase(any(), any()))
                .thenAnswer(invocation -> new PageImpl<>(List.of(post), invocation.getArgument(1), 1));

        Page<Post> result = postService.searchPosts("  spring  ", 0, 5, "id", "ASC");

        assertEquals(1, result.getTotalElements());
        verify(postRepository).findByContentContainingIgnoreCase("spring", result.getPageable());
    }

    @Test
    void rejectsPaginationBoundsAndUnapprovedSorts() {
        assertThrows(IllegalArgumentException.class, () -> postService.getAllPosts(-1, 10, "id", "desc"));
        assertThrows(IllegalArgumentException.class, () -> postService.getAllPosts(0, 0, "id", "desc"));
        assertThrows(IllegalArgumentException.class, () -> postService.getAllPosts(0, 101, "id", "desc"));
        assertThrows(IllegalArgumentException.class, () -> postService.getAllPosts(0, 10, "unknown", "desc"));
        assertThrows(IllegalArgumentException.class, () -> postService.getAllPosts(0, 10, "id", "sideways"));
    }
}