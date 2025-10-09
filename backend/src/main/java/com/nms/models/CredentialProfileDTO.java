package com.nms.models;

import java.util.UUID;

/**
 * Data Transfer Object for Credential Profile operations
 * Used for API requests and responses
 */
public class CredentialProfileDTO {
    
    private UUID id;
    private String name;
    private String username;
    private String password; // Plain text password for input, null for responses
    private String privateKey; // Plain text private key for input, null for responses
    private Integer port;
    private UUID createdBy;
    private String createdByUsername; // For display purposes
    private String createdAt;
    private String updatedAt;
    
    // Default constructor
    public CredentialProfileDTO() {}
    
    // Constructor for creating new credential profiles
    public CredentialProfileDTO(String name, String username, String password, Integer port) {
        this.name = name;
        this.username = username;
        this.password = password;
        this.port = port != null ? port : 22;
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getPrivateKey() {
        return privateKey;
    }
    
    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }
    
    public Integer getPort() {
        return port;
    }
    
    public void setPort(Integer port) {
        this.port = port;
    }
    
    public UUID getCreatedBy() {
        return createdBy;
    }
    
    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }
    
    public String getCreatedByUsername() {
        return createdByUsername;
    }
    
    public void setCreatedByUsername(String createdByUsername) {
        this.createdByUsername = createdByUsername;
    }
    
    public String getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
    
    public String getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    @Override
    public String toString() {
        return "CredentialProfileDTO{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", username='" + username + '\'' +
                ", port=" + port +
                ", createdBy=" + createdBy +
                ", createdByUsername='" + createdByUsername + '\'' +
                ", createdAt='" + createdAt + '\'' +
                ", updatedAt='" + updatedAt + '\'' +
                '}';
    }
}