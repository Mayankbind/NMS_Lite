package com.nms.models;

import org.slf4j.ILoggerFactory;

import java.util.logging.Logger;

/**
 * Enum representing the real-time health status of a device
 * Maps to the database CHECK constraint values
 */
public enum DeviceStatus {
    ONLINE("online"),
    OFFLINE("offline"),
    UNKNOWN("unknown"),
    ERROR("error");
    
    private final String value;
    
    DeviceStatus(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    /**
     * Get enum from string value
     * @param value the string value
     * @return the corresponding enum or null if not found
     */
    public static DeviceStatus fromValue(String value) {
        if (value == null) {

            return null;
        }
        
        for (DeviceStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        return null;
    }
    
    @Override
    public String toString() {
        return value;
    }
}
