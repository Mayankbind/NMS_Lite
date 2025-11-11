package com.nms.utils;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
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
            var parts = cidr.split("/");
            var network = parts[0];
            var prefixLength = Integer.parseInt(parts[1]);
            
            // Convert network address to integer
            var networkAddr = InetAddress.getByName(network);
            var networkBytes = networkAddr.getAddress();
            var networkInt = bytesToLong(networkBytes);
            
            // Calculate network mask
            var mask = (0xFFFFFFFFL << (32 - prefixLength)) & 0xFFFFFFFFL;
            var networkAddress = networkInt & mask;
            
            // Calculate number of host addresses
            var hostBits = 32 - prefixLength;
            var numHosts = (1L << hostBits);
            
            // Generate IP addresses (excluding network and broadcast for /31 and smaller)
            long startHost = (prefixLength <= 30) ? 1 : 0;
            var endHost = (prefixLength <= 30) ? numHosts - 2 : numHosts - 1;
            
            for (var i = startHost; i <= endHost; i++) {
                var hostAddress = networkAddress | i;
                var ip = longToInetAddress(hostAddress);
                ipList.add(ip.getHostAddress());
            }
            
            logger.info("Parsed CIDR {} into {} IP addresses", cidr, ipList.size());
            
        } catch (IllegalArgumentException | UnknownHostException e) {
            logger.error("Error parsing CIDR {}: {}", cidr, e.getMessage(), e);
        }
        
        return ipList;
    }
    
    /**
     * Convert byte array to long (treating as unsigned)
     */
    private static long bytesToLong(byte[] bytes) {
        long result = 0;
        for (var i = 0; i < 4; i++) {
            result = (result << 8) | (bytes[i] & 0xFF);
        }
        return result;
    }
    
    /**
     * Convert long to InetAddress
     */
    private static InetAddress longToInetAddress(long address) throws UnknownHostException {
        var bytes = new byte[4];
        for (var i = 3; i >= 0; i--) {
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
                var address = InetAddress.getByName(ip);
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
        var ipList = parseCidr(cidr);
        return pingSweep(ipList, timeout);
    }
    
    /**
     * Check if a port is open on a host
     * This is much faster than SSH connection and helps filter out hosts without SSH port open
     * 
     * @param host the host IP address or hostname
     * @param port the port number to check
     * @param timeout timeout in milliseconds
     * @return true if port is open, false otherwise
     */
    public static boolean isPortOpen(String host, int port, int timeout) {
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeout);
            logger.debug("Port {} is open on {}", port, host);
            return true;
        } catch (java.io.IOException e) {
            logger.debug("Port {} is not open on {}: {}", port, host, e.getMessage());
            return false;
        }
    }
    
    /**
     * Scan for open ports on a list of IP addresses
     * This is a fast pre-filter before attempting SSH connections
     * 
     * @param ipList list of IP addresses to scan
     * @param port the port number to check
     * @param timeout timeout in milliseconds for each port check
     * @return list of IP addresses with the port open
     */
    public static List<String> portScan(List<String> ipList, int port, int timeout) {
        List<String> openPortIps = Collections.synchronizedList(new ArrayList<>());
        
        // Use parallel stream for faster port scanning
        ipList.parallelStream().forEach(ip -> {
            if (isPortOpen(ip, port, timeout)) {
                openPortIps.add(ip);
                logger.debug("Port {} is open on {}", port, ip);
            }
        });
        
        logger.info("Port scan completed: {}/{} IPs have port {} open", openPortIps.size(), ipList.size(), port);
        return openPortIps;
    }
    
    /**
     * Scan for open SSH port (default port 22) on a list of IP addresses
     * 
     * @param ipList list of IP addresses to scan
     * @param sshPort SSH port number (default 22)
     * @param timeout timeout in milliseconds for each port check
     * @return list of IP addresses with SSH port open
     */
    public static List<String> sshPortScan(List<String> ipList, int sshPort, int timeout) {
        return portScan(ipList, sshPort, timeout);
    }
}
