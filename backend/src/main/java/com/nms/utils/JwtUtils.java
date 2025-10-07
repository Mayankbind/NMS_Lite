package com.nms.utils;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import io.vertx.core.json.JsonObject;

/**
 * JWT utility class for token creation and validation
 */
public class JwtUtils {
    
    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final String issuer;
    private final String audience;
    private final int expirationSeconds;
    private final int refreshExpirationSeconds;
    
    public JwtUtils(String secret, String algorithm, String issuer, String audience, 
                   int expirationSeconds, int refreshExpirationSeconds) {
        this.algorithm = Algorithm.HMAC256(secret);
        this.verifier = JWT.require(this.algorithm)
            .withIssuer(issuer)
            .withAudience(audience)
            .build();
        this.issuer = issuer;
        this.audience = audience;
        this.expirationSeconds = expirationSeconds;
        this.refreshExpirationSeconds = refreshExpirationSeconds;
    }
    
    /**
     * Create access token
     */
    public String createAccessToken(String userId, String username, String role) {
        try {
            Instant now = Instant.now();
            Instant expiresAt = now.plusSeconds(expirationSeconds);
            
            return JWT.create()
                .withIssuer(issuer)
                .withAudience(audience)
                .withSubject(userId)
                .withClaim("username", username)
                .withClaim("role", role)
                .withClaim("type", "access")
                .withJWTId(UUID.randomUUID().toString())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiresAt))
                .sign(algorithm);
                
        } catch (JWTCreationException e) {
            throw new RuntimeException("Failed to create access token", e);
        }
    }
    
    /**
     * Create refresh token
     */
    public String createRefreshToken(String userId) {
        try {
            Instant now = Instant.now();
            Instant expiresAt = now.plusSeconds(refreshExpirationSeconds);
            
            return JWT.create()
                .withIssuer(issuer)
                .withAudience(audience)
                .withSubject(userId)
                .withClaim("type", "refresh")
                .withJWTId(UUID.randomUUID().toString())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiresAt))
                .sign(algorithm);
                
        } catch (JWTCreationException e) {
            throw new RuntimeException("Failed to create refresh token", e);
        }
    }
    
    /**
     * Verify and decode token
     */
    public DecodedJWT verifyToken(String token) {
        try {
            return verifier.verify(token);
        } catch (JWTVerificationException e) {
            throw new RuntimeException("Invalid token", e);
        }
    }
    
    /**
     * Extract user information from token
     */
    public JsonObject extractUserInfo(DecodedJWT jwt) {
        return new JsonObject()
            .put("userId", jwt.getSubject())
            .put("username", jwt.getClaim("username").asString())
            .put("role", jwt.getClaim("role").asString())
            .put("type", jwt.getClaim("type").asString())
            .put("jwtId", jwt.getId())
            .put("issuedAt", jwt.getIssuedAt().getTime())
            .put("expiresAt", jwt.getExpiresAt().getTime());
    }
    
    /**
     * Check if token is expired
     */
    public boolean isTokenExpired(DecodedJWT jwt) {
        return jwt.getExpiresAt().before(new Date());
    }
    
    /**
     * Check if token is access token
     */
    public boolean isAccessToken(DecodedJWT jwt) {
        return "access".equals(jwt.getClaim("type").asString());
    }
    
    /**
     * Check if token is refresh token
     */
    public boolean isRefreshToken(DecodedJWT jwt) {
        return "refresh".equals(jwt.getClaim("type").asString());
    }
    
    /**
     * Get token expiration time in seconds
     */
    public long getTokenExpirationTime(DecodedJWT jwt) {
        return jwt.getExpiresAt().getTime() / 1000;
    }
    
    /**
     * Get remaining time until token expires in seconds
     */
    public long getRemainingTime(DecodedJWT jwt) {
        long expirationTime = getTokenExpirationTime(jwt);
        long currentTime = System.currentTimeMillis() / 1000;
        return Math.max(0, expirationTime - currentTime);
    }
}

