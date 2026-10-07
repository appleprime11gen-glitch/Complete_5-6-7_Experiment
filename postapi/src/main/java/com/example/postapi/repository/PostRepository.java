package com.example.postapi.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.postapi.model.Post;

public interface PostRepository extends MongoRepository<Post, String> {

	Page<Post> findByContentContainingIgnoreCase(String keyword, Pageable pageable);

	boolean existsByIdAndOwnerId(String id, String ownerId);
}