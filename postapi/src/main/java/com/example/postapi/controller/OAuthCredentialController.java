package com.example.postapi.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.postapi.dto.ApiResponse;
import com.example.postapi.dto.OAuthCredentialMetadata;
import com.example.postapi.dto.OAuthCredentialRequest;
import com.example.postapi.security.AuthenticatedUser;
import com.example.postapi.service.OAuthCredentialService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/credentials/oauth")
public class OAuthCredentialController {

    private final OAuthCredentialService credentialService;

    public OAuthCredentialController(OAuthCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<OAuthCredentialMetadata>> create(
            @Valid @RequestBody OAuthCredentialRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>("success", "Credential stored",
                credentialService.create(user.getId(), request)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<OAuthCredentialMetadata>>> list(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(new ApiResponse<>("success", "Credentials fetched",
                credentialService.listForOwner(user.getId())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @oauthCredentialAuthorization.isOwner(#id, authentication)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        boolean administrator = user.getRoles().contains("ADMIN");
        credentialService.delete(id, user.getId(), administrator);
        return ResponseEntity.ok(new ApiResponse<>("success", "Credential deleted", null));
    }
}