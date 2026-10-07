package com.example.postapi.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.example.postapi.model.OAuthCredential;

public interface OAuthCredentialRepository extends MongoRepository<OAuthCredential, String> {
    List<OAuthCredential> findByOwnerIdOrderByCreatedAtDesc(String ownerId);
    boolean existsByIdAndOwnerId(String id, String ownerId);
}