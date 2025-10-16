package com.nms.utils;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for network operations
 * Contains static methods for network scanning and validation
 */
public class NetworkUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(NetworkUtils.class);
    
    // CIDR pattern validation: e.g., "192.168.1.0/24"
    private static final Pattern CIDR_PATTERN = Pattern.compile(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)/([0-9]|[1-2][0-9]|3[0-2])$"
    );
    
    /**
     * Validate CIDR notation format
     * @param cidr the CIDR string to validate
     * @return true if valid CIDR format, false otherwise
     */
    public static boolean isValidCidr(String cidr) {
        if (cidr == null || cidr.trim().isEmpty()) {
            return false;
        }
        return CIDR_PATTERN.matcher(cidr.trim()).matches();
    }
    
    /**
     * Parse CIDR notation and return list of IP addresses
     * @param cidr the CIDR string (e.g., "192.168.1.0/24")
     * @return list of IP addresses in the range
     */
    public static List<String> parseCidr(String cidr) {
        List<String> ipList = new ArrayList<>();
        
        if (!isValidCidr(cidr)) {
            logger.warn("Invalid CIDR format: {}", cidr);
            return ipList;
        }
        
        try {
            String[] parts = cidr.split("/");
            String network = parts[0];
            int prefixLength = Integer.parseInt(parts[1]);
            
            // Convert network address to integer
            InetAddress networkAddr = InetAddress.getByName(network);
            byte[] networkBytes = networkAddr.getAddress();
            long networkInt = bytesToLong(networkBytes);
            
            // Calculate network mask
            long mask = (0xFFFFFFFFL << (32 - prefixLength)) & 0xFFFFFFFFL;
            long networkAddress = networkInt & mask;
            
            // Calculate number of host addresses
            int hostBits = 32 - prefixLength;
            long numHosts = (1L << hostBits);
            
            // Generate IP addresses (excluding network and broadcast for /31 and smaller)
            long startHost = (prefixLength <= 30) ? 1 : 0;
            long endHost = (prefixLength <= 30) ? numHosts - 2 : numHosts - 1;
            
            for (long i = startHost; i <= endHost; i++) {
                long hostAddress = networkAddress | i;
                InetAddress ip = longToInetAddress(hostAddress);
                ipList.add(ip.getHostAddress());
            }
            
            logger.info("Parsed CIDR {} into {} IP addresses", cidr, ipList.size());
            
        } catch (IllegalArgumentException | java.net.UnknownHostException e) {
            logger.error("Error parsing CIDR {}: {}", cidr, e.getMessage(), e);
        }
        
        return ipList;
    }
    
    /**
     * Convert byte array to long (treating as unsigned)
     */
    private static long bytesToLong(byte[] bytes) {
        long result = 0;
        for (int i = 0; i < 4; i++) {
            result = (result << 8) | (bytes[i] & 0xFF);
        }
        return result;
    }
    
    /**
     * Convert long to InetAddress
     */
    private static InetAddress longToInetAddress(long address) throws java.net.UnknownHostException {
        byte[] bytes = new byte[4];
        for (int i = 3; i >= 0; i--) {
            bytes[3 - i] = (byte) ((address >> (i * 8)) & 0xFF);
        }
        return InetAddress.getByAddress(bytes);
    }
    
    /**
     * Perform ping sweep on a list of IP addresses
     * @param ipList list of IP addresses to ping
     * @param timeout timeout in milliseconds
     * @return list of reachable IP addresses
     */
    public static List<String> pingSweep(List<String> ipList, int timeout) {
        List<String> reachableIps = Collections.synchronizedList(new ArrayList<>());
        
        // Use parallel stream for faster ping sweeps
        ipList.parallelStream().forEach(ip -> {
            try {
                InetAddress address = InetAddress.getByName(ip);
                if (address.isReachable(timeout)) {
                    reachableIps.add(ip);
                    logger.debug("IP {} is reachable", ip);
                }
            } catch (java.io.IOException e) {
                logger.debug("IP {} is not reachable: {}", ip, e.getMessage());
            }
        });
        
        logger.info("Ping sweep completed: {}/{} IPs reachable", reachableIps.size(), ipList.size());
        return reachableIps;
    }
    
    /**
     * Perform ping sweep on CIDR range
     * @param cidr the CIDR string
     * @param timeout timeout in milliseconds
     * @return list of reachable IP addresses
     */
    public static List<String> pingSweepCidr(String cidr, int timeout) {
        List<String> ipList = parseCidr(cidr);
        return pingSweep(ipList, timeout);
    }
}
