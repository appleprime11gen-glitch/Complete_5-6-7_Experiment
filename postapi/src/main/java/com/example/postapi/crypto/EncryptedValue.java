package com.example.postapi.crypto;

public class EncryptedValue {

    private String keyVersion;
    private String nonce;
    private String ciphertext;
    private String authenticationTag;

    public EncryptedValue() {
    }

    public EncryptedValue(String keyVersion, String nonce, String ciphertext, String authenticationTag) {
        this.keyVersion = keyVersion;
        this.nonce = nonce;
        this.ciphertext = ciphertext;
        this.authenticationTag = authenticationTag;
    }

    public String getKeyVersion() { return keyVersion; }
    public void setKeyVersion(String keyVersion) { this.keyVersion = keyVersion; }
    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }
    public String getCiphertext() { return ciphertext; }
    public void setCiphertext(String ciphertext) { this.ciphertext = ciphertext; }
    public String getAuthenticationTag() { return authenticationTag; }
    public void setAuthenticationTag(String authenticationTag) { this.authenticationTag = authenticationTag; }
}