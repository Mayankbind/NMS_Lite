package com.nms.models;

/**
 * Enum representing the status of a discovery job
 * Maps to the database CHECK constraint values
 */
public enum DiscoveryJobStatus {
    PENDING("pending"),
    RUNNING("running"),
    COMPLETED("completed"),
    FAILED("failed");
    
    private final String value;
    
    DiscoveryJobStatus(String value) {
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
    public static DiscoveryJobStatus fromValue(String value) {
        if (value == null) {
            return null;
        }
        
        for (var status : values()) {
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
