package com.nms.services;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nms.config.ApplicationConfig;
import com.nms.utils.JwtUtils;
import com.nms.utils.PasswordUtils;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

/**
 * Authentication service for handling user authentication and authorization
 */
public class AuthService {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    
    private final DatabaseService databaseService;
    private final JwtUtils jwtUtils;
    private final ApplicationConfig appConfig;
    
    // In-memory storage for login attempts (in production, use Redis or database)
    private final ConcurrentHashMap<String, AtomicInteger> loginAttempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lockoutTimes = new ConcurrentHashMap<>();
    
    public AuthService(DatabaseService databaseService, JsonObject config) {
        this.databaseService = databaseService;
        this.appConfig = new ApplicationConfig(config);
        this.jwtUtils = new JwtUtils(
            appConfig.getJwtSecret(),
            appConfig.getJwtAlgorithm(),
            appConfig.getJwtIssuer(),
            appConfig.getJwtAudience(),
            appConfig.getJwtExpiration(),
            appConfig.getJwtRefreshExpiration()
        );
    }
    
    /**
     * Authenticate user with username and password
     */
    public Future<JsonObject> authenticate(String username, String password) {
        Promise<JsonObject> promise = Promise.promise();
        
        // Check if user is locked out
        if (isUserLockedOut(username)) {
            promise.fail("Account is temporarily locked due to too many failed login attempts");
            return promise.future();
        }
        
        // Find user by username
        databaseService.findUserByUsername(username)
            .compose(user -> {
                if (user == null) {
                    recordFailedLoginAttempt(username);
                    return Future.failedFuture("Invalid username or password");
                }
                
                // Verify password
                String storedPasswordHash = user.getString("passwordHash");
                if (!PasswordUtils.verifyPassword(password, storedPasswordHash)) {
                    recordFailedLoginAttempt(username);
                    return Future.failedFuture("Invalid username or password");
                }
                
                // Reset login attempts on successful login
                resetLoginAttempts(username);
                
                // Update last login
                return databaseService.updateUserLastLogin(user.getString("id"))
                    .map(v -> user);
            })
            .onSuccess(user -> {
                // Create tokens
                String accessToken = jwtUtils.createAccessToken(
                    user.getString("id"),
                    user.getString("username"),
                    user.getString("role")
                );
                
                String refreshToken = jwtUtils.createRefreshToken(user.getString("id"));
                
                JsonObject authResult = new JsonObject()
                    .put("accessToken", accessToken)
                    .put("refreshToken", refreshToken)
                    .put("tokenType", "Bearer")
                    .put("expiresIn", appConfig.getJwtExpiration())
                    .put("user", new JsonObject()
                        .put("id", user.getString("id"))
                        .put("username", user.getString("username"))
                        .put("email", user.getString("email"))
                        .put("role", user.getString("role"))
                    );
                
                logger.info("User {} authenticated successfully", username);
                promise.complete(authResult);
            })
            .onFailure(throwable -> {
                logger.warn("Authentication failed for user {}: {}", username, throwable.getMessage());
                promise.fail(throwable);
            });
        
        return promise.future();
    }
    
    /**
     * Refresh access token using refresh token
     */
    public Future<JsonObject> refreshToken(String refreshToken) {
        Promise<JsonObject> promise = Promise.promise();
        
        try {
            // Verify refresh token
            var decodedJWT = jwtUtils.verifyToken(refreshToken);
            
            if (!jwtUtils.isRefreshToken(decodedJWT)) {
                promise.fail("Invalid token type");
                return promise.future();
            }
            
            // Get user information
            String userId = decodedJWT.getSubject();
            
            // Find user in database
            databaseService.findUserById(userId)
                .onSuccess(user -> {
                    if (user == null) {
                        promise.fail("User not found");
                        return;
                    }
                    
                    // Create new access token
                    String newAccessToken = jwtUtils.createAccessToken(
                        user.getString("id"),
                        user.getString("username"),
                        user.getString("role")
                    );
                    
                    JsonObject tokenResult = new JsonObject()
                        .put("accessToken", newAccessToken)
                        .put("tokenType", "Bearer")
                        .put("expiresIn", appConfig.getJwtExpiration());
                    
                    logger.info("Token refreshed for user {}", user.getString("username"));
                    promise.complete(tokenResult);
                })
                .onFailure(throwable -> {
                    logger.error("Failed to refresh token", throwable);
                    promise.fail(throwable);
                });
                
        } catch (Exception e) {
            logger.warn("Invalid refresh token: {}", e.getMessage());
            promise.fail("Invalid refresh token");
        }
        
        return promise.future();
    }
    
    /**
     * Validate access token
     */
    public Future<JsonObject> validateToken(String token) {
        Promise<JsonObject> promise = Promise.promise();
        
        try {
            // Verify token
            var decodedJWT = jwtUtils.verifyToken(token);
            
            if (!jwtUtils.isAccessToken(decodedJWT)) {
                promise.fail("Invalid token type");
                return promise.future();
            }
            
            // Extract user information
            JsonObject userInfo = jwtUtils.extractUserInfo(decodedJWT);
            
            // Verify user still exists in database
            databaseService.findUserById(userInfo.getString("userId"))
                .onSuccess(user -> {
                    if (user == null) {
                        promise.fail("User not found");
                        return;
                    }
                    
                    // Update user info with current data
                    userInfo.put("username", user.getString("username"))
                           .put("email", user.getString("email"))
                           .put("role", user.getString("role"));
                    
                    promise.complete(userInfo);
                })
                .onFailure(throwable -> {
                    logger.error("Failed to validate token", throwable);
                    promise.fail(throwable);
                });
                
        } catch (Exception e) {
            logger.warn("Token validation failed: {}", e.getMessage());
            promise.fail("Invalid token");
        }
        
        return promise.future();
    }
    
    /**
     * Register new user
     */
    public Future<JsonObject> registerUser(String username, String email, String password, String role) {
        Promise<JsonObject> promise = Promise.promise();
        
        // Validate password
        PasswordUtils.PasswordValidationResult validation = PasswordUtils.validatePassword(
            password,
            appConfig.getPasswordMinLength(),
            appConfig.isPasswordRequireUppercase(),
            appConfig.isPasswordRequireLowercase(),
            appConfig.isPasswordRequireNumbers(),
            appConfig.isPasswordRequireSpecialChars()
        );
        
        if (!validation.isValid()) {
            promise.fail("Password validation failed: " + validation.getErrorMessage());
            return promise.future();
        }
        
        // Check if username already exists
        databaseService.usernameExists(username)
            .compose(usernameExists -> {
                if (usernameExists) {
                    return Future.failedFuture("Username already exists");
                }
                
                // Check if email already exists
                return databaseService.emailExists(email);
            })
            .compose(emailExists -> {
                if (emailExists) {
                    return Future.failedFuture("Email already exists");
                }
                
                // Hash password and create user
                String passwordHash = PasswordUtils.hashPassword(password);
                return databaseService.createUser(username, email, passwordHash, role);
            })
            .onSuccess(user -> {
                logger.info("User {} registered successfully", username);
                promise.complete(user);
            })
            .onFailure(throwable -> {
                logger.error("Failed to register user", throwable);
                promise.fail(throwable);
            });
        
        return promise.future();
    }
    
    /**
     * Check if user is locked out
     */
    private boolean isUserLockedOut(String username) {
        Long lockoutTime = lockoutTimes.get(username);
        if (lockoutTime != null && System.currentTimeMillis() < lockoutTime) {
            return true;
        }
        
        // Remove expired lockout
        if (lockoutTime != null && System.currentTimeMillis() >= lockoutTime) {
            lockoutTimes.remove(username);
            loginAttempts.remove(username);
        }
        
        return false;
    }
    
    /**
     * Record failed login attempt
     */
    private void recordFailedLoginAttempt(String username) {
        AtomicInteger attempts = loginAttempts.computeIfAbsent(username, k -> new AtomicInteger(0));
        int currentAttempts = attempts.incrementAndGet();
        
        if (currentAttempts >= appConfig.getMaxLoginAttempts()) {
            long lockoutDuration = appConfig.getLockoutDuration() * 1000L; // Convert to milliseconds
            long lockoutTime = System.currentTimeMillis() + lockoutDuration;
            lockoutTimes.put(username, lockoutTime);
            
            logger.warn("User {} locked out for {} seconds due to {} failed login attempts", 
                username, appConfig.getLockoutDuration(), currentAttempts);
        }
    }
    
    /**
     * Reset login attempts
     */
    private void resetLoginAttempts(String username) {
        loginAttempts.remove(username);
        lockoutTimes.remove(username);
    }
    
    /**
     * Get JWT utils instance
     */
    public JwtUtils getJwtUtils() {
        return jwtUtils;
    }
}

