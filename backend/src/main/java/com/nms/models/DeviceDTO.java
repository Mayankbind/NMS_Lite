package com.nms.models;

import java.util.UUID;

import io.vertx.core.json.JsonObject;

/**
 * Data Transfer Object for Device operations
 * Used for API requests and responses
 */
public class DeviceDTO {
    
    private UUID id;
    private String hostname;
    private String ipAddress;
    private String deviceType;
    private JsonObject osInfo;
    private UUID credentialProfileId;
    private String status;
    private String lastSeen;
    private String createdAt;
    private String updatedAt;
    
    // Default constructor
    public DeviceDTO() {}
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public String getHostname() {
        return hostname;
    }
    
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }
    
    public String getIpAddress() {
        return ipAddress;
    }
    
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
    
    public String getDeviceType() {
        return deviceType;
    }
    
    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }
    
    public JsonObject getOsInfo() {
        return osInfo;
    }
    
    public void setOsInfo(JsonObject osInfo) {
        this.osInfo = osInfo;
    }
    
    public UUID getCredentialProfileId() {
        return credentialProfileId;
    }
    
    public void setCredentialProfileId(UUID credentialProfileId) {
        this.credentialProfileId = credentialProfileId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getLastSeen() {
        return lastSeen;
    }
    
    public void setLastSeen(String lastSeen) {
        this.lastSeen = lastSeen;
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
        return "DeviceDTO{" +
                "id=" + id +
                ", hostname='" + hostname + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", deviceType='" + deviceType + '\'' +
                ", credentialProfileId=" + credentialProfileId +
                ", status='" + status + '\'' +
                ", lastSeen='" + lastSeen + '\'' +
                ", createdAt='" + createdAt + '\'' +
                ", updatedAt='" + updatedAt + '\'' +
                '}';
    }
}
