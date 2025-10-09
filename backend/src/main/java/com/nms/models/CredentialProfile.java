package com.nms.models;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Credential Profile model for storing SSH credentials
 */
public class CredentialProfile {
    
    private UUID id;
    private String name;
    private String username;
    private String passwordEncrypted;
    private String privateKeyEncrypted;
    private Integer port;
    private UUID createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Default constructor
    public CredentialProfile() {}
    
    // Constructor for creating new credential profiles
    public CredentialProfile(String name, String username, String passwordEncrypted, 
                           Integer port, UUID createdBy) {
        this.name = name;
        this.username = username;
        this.passwordEncrypted = passwordEncrypted;
        this.port = port != null ? port : 22;
        this.createdBy = createdBy;
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
    
    public String getPasswordEncrypted() {
        return passwordEncrypted;
    }
    
    public void setPasswordEncrypted(String passwordEncrypted) {
        this.passwordEncrypted = passwordEncrypted;
    }
    
    public String getPrivateKeyEncrypted() {
        return privateKeyEncrypted;
    }
    
    public void setPrivateKeyEncrypted(String privateKeyEncrypted) {
        this.privateKeyEncrypted = privateKeyEncrypted;
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
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    @Override
    public String toString() {
        return "CredentialProfile{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", username='" + username + '\'' +
                ", port=" + port +
                ", createdBy=" + createdBy +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}