package com.nms.utils;

import java.security.SecureRandom;
import java.util.regex.Pattern;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Password utility class for hashing, validation, and generation
 */
public class PasswordUtils {
    
    private static final int BCRYPT_ROUNDS = 12;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    // Password validation patterns
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]");
    
    /**
     * Hash password using BCrypt
     */
    public static String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(BCRYPT_ROUNDS));
    }
    
    /**
     * Verify password against hash
     */
    public static boolean verifyPassword(String password, String hash) {
        try {
            return BCrypt.checkpw(password, hash);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Validate password strength
     */
    public static PasswordValidationResult validatePassword(String password, 
                                                          int minLength,
                                                          boolean requireUppercase,
                                                          boolean requireLowercase,
                                                          boolean requireNumbers,
                                                          boolean requireSpecialChars) {

        var result = new PasswordValidationResult();
        
        if (password == null || password.length() < minLength) {
            result.addError("Password must be at least " + minLength + " characters long");
        }
        
        if (requireUppercase && !UPPERCASE_PATTERN.matcher(password).find()) {
            result.addError("Password must contain at least one uppercase letter");
        }
        
        if (requireLowercase && !LOWERCASE_PATTERN.matcher(password).find()) {
            result.addError("Password must contain at least one lowercase letter");
        }
        
        if (requireNumbers && !DIGIT_PATTERN.matcher(password).find()) {
            result.addError("Password must contain at least one number");
        }
        
        if (requireSpecialChars && !SPECIAL_CHAR_PATTERN.matcher(password).find()) {
            result.addError("Password must contain at least one special character");
        }
        
        return result;
    }
    
    /**
     * Generate secure random password
     */
    public static String generateSecurePassword(int length) {
        var chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+-=[]{}|;:,.<>?";
        var password = new StringBuilder(length);
        
        for (var i = 0; i < length; i++) {
            password.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
        }
        
        return password.toString();
    }
    
    /**
     * Generate secure random salt
     */
    public static String generateSalt() {
        var salt = new byte[16];
        SECURE_RANDOM.nextBytes(salt);
        return java.util.Base64.getEncoder().encodeToString(salt);
    }
    
    /**
     * Password validation result
     */
    public static class PasswordValidationResult {
        private final java.util.List<String> errors = new java.util.ArrayList<>();
        
        public void addError(String error) {
            errors.add(error);
        }
        
        public boolean isValid() {
            return errors.isEmpty();
        }
        
        public java.util.List<String> getErrors() {
            return new java.util.ArrayList<>(errors);
        }
        
        public String getErrorMessage() {
            return String.join(", ", errors);
        }
    }
    
    /**
     * Simple BCrypt implementation
     * Note: In production, use a proper BCrypt library like jBCrypt
     */
//    private static class BCrypt {
//        private static final String SALT_PREFIX = "$2a$";
//        private static final String SALT_SUFFIX = "$";
//
//        public static String hashpw(String password, String salt) {
//            // This is a simplified implementation
//            // In production, use a proper BCrypt library
//            return salt + java.util.Base64.getEncoder().encodeToString(password.getBytes());
//        }
//
//        public static String gensalt(int rounds) {
//            byte[] saltBytes = new byte[16];
//            SECURE_RANDOM.nextBytes(saltBytes);
//            return SALT_PREFIX + String.format("%02d", rounds) + SALT_SUFFIX +
//                   java.util.Base64.getEncoder().encodeToString(saltBytes);
//        }
//
//        public static boolean checkpw(String password, String hash) {
//            if (hash == null || password == null) {
//                return false;
//            }
//
//            try {
//                // Extract salt from hash
//                String salt = hash.substring(0, hash.lastIndexOf('$') + 1);
//                String expectedHash = hashpw(password, salt);
//                return expectedHash.equals(hash);
//            } catch (Exception e) {
//                return false;
//            }
//        }
//    }
}

