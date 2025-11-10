package com.nms.services;

import java.util.List;
import java.util.UUID;

import com.nms.models.Device;
import com.nms.models.DiscoveryJob;
import com.nms.models.DiscoveryJobDTO;

import io.vertx.core.Future;

/**
 * Interface for Discovery Service operations
 * Allows both DiscoveryService and DiscoveryServiceProxy to be used interchangeably
 */
public interface IDiscoveryService {
    
    /**
     * Start a new discovery job
     * @param request the discovery job request
     * @param userId the user ID creating the job
     * @return Future containing the job ID
     */
    Future<UUID> startDiscovery(DiscoveryJobDTO request, UUID userId);
    
    /**
     * Get discovery job status
     * @param jobId the job ID
     * @param userId the user ID
     * @return Future containing the discovery job
     */
    Future<DiscoveryJob> getDiscoveryStatus(UUID jobId, UUID userId);
    
    /**
     * Get discovery results (discovered devices)
     * @param jobId the job ID
     * @param userId the user ID
     * @return Future containing list of discovered devices
     */
    Future<List<Device>> getDiscoveryResults(UUID jobId, UUID userId);
    
    /**
     * Cancel a discovery job
     * @param jobId the job ID
     * @param userId the user ID
     * @return Future indicating success
     */
    Future<Void> cancelDiscovery(UUID jobId, UUID userId);
}

