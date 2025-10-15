package com.nms.models;

import java.util.UUID;

import io.vertx.core.json.JsonObject;

/**
 * Data Transfer Object for Discovery Job operations
 * Used for API requests and responses
 */
public class DiscoveryJobDTO {
    
    private UUID id;
    private String name;
    private String status;
    private UUID credentialProfileId;
    private String targetRange;
    private JsonObject results;
    private String startedAt;
    private String completedAt;
    private UUID createdBy;
    private String createdByUsername; // For display purposes
    private String createdAt;
    
    // Default constructor
    public DiscoveryJobDTO() {}
    
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
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public UUID getCredentialProfileId() {
        return credentialProfileId;
    }
    
    public void setCredentialProfileId(UUID credentialProfileId) {
        this.credentialProfileId = credentialProfileId;
    }
    
    public String getTargetRange() {
        return targetRange;
    }
    
    public void setTargetRange(String targetRange) {
        this.targetRange = targetRange;
    }
    
    public JsonObject getResults() {
        return results;
    }
    
    public void setResults(JsonObject results) {
        this.results = results;
    }
    
    public String getStartedAt() {
        return startedAt;
    }
    
    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
    }
    
    public String getCompletedAt() {
        return completedAt;
    }
    
    public void setCompletedAt(String completedAt) {
        this.completedAt = completedAt;
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
    
    @Override
    public String toString() {
        return "DiscoveryJobDTO{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", status='" + status + '\'' +
                ", credentialProfileId=" + credentialProfileId +
                ", targetRange='" + targetRange + '\'' +
                ", startedAt='" + startedAt + '\'' +
                ", completedAt='" + completedAt + '\'' +
                ", createdBy=" + createdBy +
                ", createdByUsername='" + createdByUsername + '\'' +
                ", createdAt='" + createdAt + '\'' +
                '}';
    }
}
