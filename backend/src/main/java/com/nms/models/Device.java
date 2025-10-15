package com.nms.models;

import java.time.LocalDateTime;
import java.util.UUID;

import io.vertx.core.json.JsonObject;

/**
 * Device model for storing discovered and monitored devices
 * Maps to the devices table in the database
 */
public class Device {
    
    private UUID id;
    private String hostname;
    private String ipAddress;
    private String deviceType;
    private JsonObject osInfo;
    private UUID credentialProfileId;
    private DeviceStatus status;
    private LocalDateTime lastSeen;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Default constructor
    public Device() {}
    
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
    
    public DeviceStatus getStatus() {
        return status;
    }
    
    public void setStatus(DeviceStatus status) {
        this.status = status;
    }
    
    public LocalDateTime getLastSeen() {
        return lastSeen;
    }
    
    public void setLastSeen(LocalDateTime lastSeen) {
        this.lastSeen = lastSeen;
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
        return "Device{" +
                "id=" + id +
                ", hostname='" + hostname + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", deviceType='" + deviceType + '\'' +
                ", credentialProfileId=" + credentialProfileId +
                ", status=" + status +
                ", lastSeen=" + lastSeen +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
