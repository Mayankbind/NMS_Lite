package com.nms.middleware;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Rate limiting middleware using token bucket algorithm
 */
public class RateLimitMiddleware implements Handler<RoutingContext> {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitMiddleware.class);
    
    private final JsonObject config;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    
    public RateLimitMiddleware(JsonObject config) {
        this.config = config;
    }
    
    public static RateLimitMiddleware create(JsonObject config) {
        return new RateLimitMiddleware(config);
    }
    
    @Override
    public void handle(RoutingContext ctx) {
        var rateLimitConfig = config.getJsonObject("server.rateLimit", new JsonObject());
        
        if (!rateLimitConfig.getBoolean("enabled", true)) {
            ctx.next();
            return;
        }

        var clientId = getClientIdentifier(ctx);
        int requestsPerMinute = rateLimitConfig.getInteger("requestsPerMinute", 100);
        int burstSize = rateLimitConfig.getInteger("burstSize", 20);

        var bucket = buckets.computeIfAbsent(clientId,
            k -> new TokenBucket(requestsPerMinute, burstSize));
        
        if (bucket.tryConsume()) {
            // Add rate limit headers
            ctx.response()
                .putHeader("X-RateLimit-Limit", String.valueOf(requestsPerMinute))
                .putHeader("X-RateLimit-Remaining", String.valueOf(bucket.getRemainingTokens()))
                .putHeader("X-RateLimit-Reset", String.valueOf(bucket.getResetTime()));
            
            ctx.next();
        } else {
            logger.warn("Rate limit exceeded for client: {}", clientId);
            sendRateLimitExceededResponse(ctx, bucket);
        }
    }
    
    private String getClientIdentifier(RoutingContext ctx) {
        // Use IP address as client identifier
        var xForwardedFor = ctx.request().getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        var xRealIp = ctx.request().getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return ctx.request().remoteAddress().host();
    }
    
    private void sendRateLimitExceededResponse(RoutingContext ctx, TokenBucket bucket) {
        ctx.response()
            .setStatusCode(429)
            .putHeader("Content-Type", "application/json")
            .putHeader("Retry-After", String.valueOf(bucket.getTimeUntilReset()))
            .putHeader("X-RateLimit-Limit", String.valueOf(bucket.getCapacity()))
            .putHeader("X-RateLimit-Remaining", "0")
            .putHeader("X-RateLimit-Reset", String.valueOf(bucket.getResetTime()))
            .end(new JsonObject()
                .put("error", "Too Many Requests")
                .put("message", "Rate limit exceeded. Please try again later.")
                .put("retryAfter", bucket.getTimeUntilReset())
                .put("timestamp", System.currentTimeMillis())
                .encode());
    }
    
    /**
     * Token bucket implementation for rate limiting
     */
    private static class TokenBucket {
        private final int capacity;
        private final int refillRate; // tokens per minute
        private final AtomicInteger tokens;
        private final AtomicLong lastRefill;
        
        public TokenBucket(int refillRate, int capacity) {
            this.refillRate = refillRate;
            this.capacity = capacity;
            this.tokens = new AtomicInteger(capacity);
            this.lastRefill = new AtomicLong(System.currentTimeMillis());
        }
        
        public boolean tryConsume() {
            refill();
            var currentTokens = tokens.get();
            if (currentTokens > 0) {
                return tokens.compareAndSet(currentTokens, currentTokens - 1);
            }
            return false;
        }
        
        private void refill() {
            var now = System.currentTimeMillis();
            var lastRefillTime = lastRefill.get();
            var timePassed = now - lastRefillTime;
            
            if (timePassed >= 60000) { // 1 minute
                var tokensToAdd = (int) (timePassed / 60000 * refillRate);
                var currentTokens = tokens.get();
                var newTokens = Math.min(capacity, currentTokens + tokensToAdd);
                
                if (tokens.compareAndSet(currentTokens, newTokens)) {
                    lastRefill.set(now);
                }
            }
        }
        
        public int getRemainingTokens() {
            refill();
            return tokens.get();
        }
        
        public int getCapacity() {
            return capacity;
        }
        
        public long getResetTime() {
            return lastRefill.get() + 60000; // Next minute
        }
        
        public long getTimeUntilReset() {
            var resetTime = getResetTime();
            var now = System.currentTimeMillis();
            return Math.max(0, (resetTime - now) / 1000);
        }
    }
}
