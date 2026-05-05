package com.mirror.accessors;


import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;

import java.util.Optional;

@Slf4j
@AllArgsConstructor
public class RedisAccessor {
    private static final int MAX_RETRIES = 3;
    private final RedisClient redisClient;
    private final String NIL = "nil";

    public void set(String key, String value) throws InterruptedException,JedisException{
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                redisClient.set(key, value);
                log.info("Successfully set key: {}", key);
                return; // Exit if successful
            } catch (JedisConnectionException e) {
                log.warn("Attempt {} to set key failed: {}", attempt, key, e);
                try {
                    Thread.sleep(1000 * (1L << attempt)); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Thread interrupted during Redis set operation");
                    throw ie;
                }
            }
        }
        log.warn("Failed to set key in Redis after " + MAX_RETRIES + " attempts");
        throw new JedisException("Failed to set key in Redis after " + MAX_RETRIES + " attempts");
    }

    public Optional<String> get(String key) throws InterruptedException,JedisException{
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String value = redisClient.get(key);
                log.info("Successfully retrieved value for key: {}", key);
                return Optional.ofNullable(value).filter(v -> !NIL.equals(v));
            } catch (JedisConnectionException e) {
                log.warn("Attempt {} to get key failed: {}", attempt, key, e);
                try {
                    Thread.sleep(1000 * (1L << attempt)); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Thread interrupted during Redis get operation");
                    throw ie;
                }
            } 
        }
        log.error("Failed to retrieve key: {} from Redis after {} attempts", key, MAX_RETRIES);
        throw new JedisException("Failed to get key from Redis after " + MAX_RETRIES + " attempts");
    }
    
}
