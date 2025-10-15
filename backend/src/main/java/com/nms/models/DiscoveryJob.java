package com.nms.models;

import java.time.LocalDateTime;
import java.util.UUID;

import io.vertx.core.json.JsonObject;

/**
 * Discovery Job model for tracking network discovery operations
 * Maps to the discovery_jobs table in the database
 */
public class DiscoveryJob {
    
    private UUID id;
    private String name;
    private DiscoveryJobStatus status;
    private UUID credentialProfileId;
    private String targetRange;
    private JsonObject results;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private UUID createdBy;
    private LocalDateTime createdAt;
    
    // Default constructor
    public DiscoveryJob() {}
    
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
    
    public DiscoveryJobStatus getStatus() {
        return status;
    }
    
    public void setStatus(DiscoveryJobStatus status) {
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
    
    public LocalDateTime getStartedAt() {
        return startedAt;
    }
    
    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }
    
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
    
    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
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
    
    @Override
    public String toString() {
        return "DiscoveryJob{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", status=" + status +
                ", credentialProfileId=" + credentialProfileId +
                ", targetRange='" + targetRange + '\'' +
                ", startedAt=" + startedAt +
                ", completedAt=" + completedAt +
                ", createdBy=" + createdBy +
                ", createdAt=" + createdAt +
                '}';
    }
}
