package com.example.postapi.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.example.postapi.model.RefreshTokenFamily;

public interface RefreshTokenFamilyRepository extends MongoRepository<RefreshTokenFamily, String> {
    Optional<RefreshTokenFamily> findByCurrentTokenHash(String tokenHash);
    Optional<RefreshTokenFamily> findByPreviousTokenHashesContaining(String tokenHash);
}