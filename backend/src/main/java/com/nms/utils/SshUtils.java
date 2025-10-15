package com.nms.utils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import io.vertx.core.json.JsonObject;

/**
 * Utility class for SSH operations
 * Contains static methods for SSH connectivity testing and device information gathering
 */
public class SshUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(SshUtils.class);
    
    /**
     * Test SSH connection to a device
     * @param ip the IP address to connect to
     * @param username SSH username
     * @param password SSH password
     * @param port SSH port (default 22)
     * @param timeout connection timeout in milliseconds
     * @return true if connection successful, false otherwise
     */
    public static boolean testConnection(String ip, String username, String password, int port, int timeout) {
        Session session = null;
        
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(username, ip, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setTimeout(timeout);
            session.connect();
            
            logger.debug("SSH connection successful to {}:{}", ip, port);
            return true;
            
        } catch (com.jcraft.jsch.JSchException e) {
            logger.debug("SSH connection failed to {}:{} - {}", ip, port, e.getMessage());
            return false;
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }
    
    /**
     * Gather device information via SSH
     * @param ip the IP address to connect to
     * @param username SSH username
     * @param password SSH password
     * @param port SSH port (default 22)
     * @param timeout connection timeout in milliseconds
     * @return JsonObject containing device information
     */
    public static JsonObject gatherDeviceInfo(String ip, String username, String password, int port, int timeout) {
        Session session = null;
        JsonObject deviceInfo = new JsonObject();
        
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(username, ip, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setTimeout(timeout);
            session.connect();
            
            // Gather basic system information
            deviceInfo.put("hostname", executeCommand(session, "hostname"));
            deviceInfo.put("os", executeCommand(session, "uname -s"));
            deviceInfo.put("osVersion", executeCommand(session, "uname -r"));
            deviceInfo.put("architecture", executeCommand(session, "uname -m"));
            deviceInfo.put("uptime", executeCommand(session, "uptime"));
            deviceInfo.put("cpuInfo", executeCommand(session, "cat /proc/cpuinfo | grep 'model name' | head -1"));
            deviceInfo.put("memoryInfo", executeCommand(session, "free -h"));
            deviceInfo.put("diskInfo", executeCommand(session, "df -h"));
            
            // Determine device type based on OS
            String os = deviceInfo.getString("os", "").toLowerCase();
            if (os.contains("linux")) {
                deviceInfo.put("deviceType", "linux");
            } else if (os.contains("darwin")) {
                deviceInfo.put("deviceType", "macos");
            } else if (os.contains("windows")) {
                deviceInfo.put("deviceType", "windows");
            } else {
                deviceInfo.put("deviceType", "unknown");
            }
            
            logger.info("Gathered device info for {}: {}", ip, deviceInfo.getString("hostname"));
            
        } catch (com.jcraft.jsch.JSchException e) {
            logger.error("Error gathering device info for {}: {}", ip, e.getMessage(), e);
            deviceInfo.put("error", e.getMessage());
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
        
        return deviceInfo;
    }
    
    /**
     * Execute a command via SSH and return the output
     * @param session the SSH session
     * @param command the command to execute
     * @return the command output as string
     */
    private static String executeCommand(Session session, String command) {
        ChannelExec channel = null;
        
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            
            InputStream inputStream;
            try {
                inputStream = channel.getInputStream();
            } catch (java.io.IOException e) {
                logger.debug("Error getting input stream: {}", e.getMessage());
                return "unknown";
            }
            channel.connect();
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int bytesRead;
            
            try {
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            } catch (java.io.IOException e) {
                logger.debug("Error reading command output: {}", e.getMessage());
            }
            
            String result = outputStream.toString().trim();
            return result.isEmpty() ? "unknown" : result;
            
        } catch (com.jcraft.jsch.JSchException e) {
            logger.debug("Error executing command '{}': {}", command, e.getMessage());
            return "unknown";
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
    }
    
    /**
     * Get supported device types
     * @return map of device types and their descriptions
     */
    public static Map<String, String> getSupportedDeviceTypes() {
        Map<String, String> deviceTypes = new HashMap<>();
        deviceTypes.put("linux", "Linux Server");
        deviceTypes.put("macos", "macOS Server");
        deviceTypes.put("windows", "Windows Server");
        deviceTypes.put("network", "Network Device");
        deviceTypes.put("unknown", "Unknown Device Type");
        return deviceTypes;
    }
}
