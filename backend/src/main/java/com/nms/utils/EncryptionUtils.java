package com.nms.utils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for encrypting and decrypting sensitive data like passwords and SSH keys
 * Uses AES-256-GCM encryption for strong security
 */
public class EncryptionUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(EncryptionUtils.class);
    
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits
    private static final int GCM_TAG_LENGTH = 16; // 128 bits
    private static final int AES_KEY_LENGTH = 256; // 256 bits
    
    private final SecretKey secretKey;
    private final SecureRandom secureRandom;
    
    public EncryptionUtils(String secretKey) {
        SecretKey secretKey1;
//        this.secretKey = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        
        //Gemini solution
//        this.secretKey = new SecretKeySpec(Base64.getDecoder().decode(secretKey), ALGORITHM);
//        this.secureRandom = new SecureRandom();
        
        //Cursor solution
        try {
            // First try standard Base64 decoder
            var keyBytes = Base64.getDecoder().decode(secretKey);
            secretKey1 = new SecretKeySpec(keyBytes, ALGORITHM);
        } catch (IllegalArgumentException e) {
            // If that fails, try URL-safe Base64 decoder
            try {
                var keyBytes = Base64.getUrlDecoder().decode(secretKey);
                secretKey1 = new SecretKeySpec(keyBytes, ALGORITHM);
            } catch (IllegalArgumentException e2) {
                // If both fail, try to clean the key and use standard decoder
                var cleanedKey = secretKey.replace('-', '+').replace('_', '/');
                // Add padding if needed
                while (cleanedKey.length() % 4 != 0) {
                    cleanedKey += "=";
                }
                var keyBytes = Base64.getDecoder().decode(cleanedKey);
                secretKey1 = new SecretKeySpec(keyBytes, ALGORITHM);
            }
        }
        this.secretKey = secretKey1;
        this.secureRandom = new SecureRandom();
    }
    
    /**
     * Generate a random AES-256 key
     */
    public static String generateSecretKey() {
        try {
            var keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(AES_KEY_LENGTH);
            var key = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (java.security.NoSuchAlgorithmException e) {
            logger.error("Failed to generate secret key", e);
            throw new RuntimeException("Failed to generate secret key", e);
        }
    }
    
    /**
     * Encrypt a plain text string
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        
        try {
            // Generate random IV
            var iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            // Initialize cipher
            var cipher = Cipher.getInstance(TRANSFORMATION);
            var gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec);
            
            // Encrypt
            var encryptedData = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            
            // Combine IV and encrypted data
            var encryptedWithIv = new byte[GCM_IV_LENGTH + encryptedData.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedData, 0, encryptedWithIv, GCM_IV_LENGTH, encryptedData.length);
            
            return Base64.getEncoder().encodeToString(encryptedWithIv);
            
        } catch (javax.crypto.NoSuchPaddingException | javax.crypto.BadPaddingException | 
                 javax.crypto.IllegalBlockSizeException | java.security.InvalidKeyException | 
                 java.security.InvalidAlgorithmParameterException | java.security.NoSuchAlgorithmException e) {
            logger.error("Failed to encrypt data", e);
            throw new RuntimeException("Failed to encrypt data", e);
        }
    }
    
    /**
     * Decrypt an encrypted string
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }
        
        try {
            // Decode from Base64
            var encryptedWithIv = Base64.getDecoder().decode(encryptedText);
            
            // Extract IV
            var iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(encryptedWithIv, 0, iv, 0, GCM_IV_LENGTH);
            
            // Extract encrypted data
            var encryptedData = new byte[encryptedWithIv.length - GCM_IV_LENGTH];
            System.arraycopy(encryptedWithIv, GCM_IV_LENGTH, encryptedData, 0, encryptedData.length);
            
            // Initialize cipher
            var cipher = Cipher.getInstance(TRANSFORMATION);
            var gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec);
            
            // Decrypt
            var decryptedData = cipher.doFinal(encryptedData);
            
            return new String(decryptedData, StandardCharsets.UTF_8);
            
        } catch (javax.crypto.NoSuchPaddingException | javax.crypto.BadPaddingException | 
                 javax.crypto.IllegalBlockSizeException | java.security.InvalidKeyException | 
                 java.security.InvalidAlgorithmParameterException | java.security.NoSuchAlgorithmException e) {
            logger.error("Failed to decrypt data", e);
            throw new RuntimeException("Failed to decrypt data", e);
        }
    }
    
    /**
     * Check if a string appears to be encrypted (Base64 encoded)
     */
    public boolean isEncrypted(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        
        try {
            var decoded = Base64.getDecoder().decode(text);
            // Check if it's long enough to contain IV + some encrypted data
            return decoded.length > GCM_IV_LENGTH;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}