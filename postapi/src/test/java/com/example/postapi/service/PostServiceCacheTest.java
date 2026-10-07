package com.example.postapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

import com.example.postapi.config.CacheConfig;
import com.example.postapi.model.Post;
import com.example.postapi.repository.PostRepository;

class PostServiceCacheTest {

    @Test
    void cachesIdReadsAndEvictsThemAfterAnUpdate() {
        PostRepository repository = mock(PostRepository.class);
        Post post = new Post("before");
        post.setId("post-1");
        when(repository.findById("post-1")).thenReturn(Optional.of(post));
        when(repository.save(post)).thenReturn(post);

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("test", Map.of("app.cache.enabled", "true")));
            context.getBeanFactory().registerSingleton("postRepository", repository);
            context.register(CacheTestConfiguration.class, CacheConfig.class);
            context.refresh();

            PostService service = context.getBean(PostService.class);
            assertEquals("before", service.getPostById("post-1").getContent());
            service.getPostById("post-1");
            verify(repository, times(1)).findById("post-1");

            service.updatePost("post-1", "after");
            assertEquals("after", service.getPostById("post-1").getContent());
            verify(repository, times(3)).findById("post-1");
        }
    }

    @Configuration
    @EnableCaching
    static class CacheTestConfiguration {
        @Bean
        PostService postService(PostRepository repository) {
            return new PostService(repository);
        }
    }
}