package com.mirror.accessors;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import org.mockito.MockitoAnnotations;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.exceptions.JedisConnectionException;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Optional;


@ExtendWith(MockitoExtension.class)
@Slf4j
public class RedisAccessorTest {
    @Mock
    private static RedisClient redisClient;
    private static RedisAccessor redisAccessor;

    private static final String TEST_KEY = "test_key";
    private static final String TEST_VALUE = "test_value";

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        redisAccessor = new RedisAccessor(redisClient);
    }

    @Test
    public void testSet() {
        try{
            // Implement test for set method
            Mockito.when(redisClient.set(TEST_KEY, TEST_VALUE)).thenReturn("OK");
            redisAccessor.set(TEST_KEY, TEST_VALUE);
            Mockito.verify(redisClient, Mockito.times(1)).set(TEST_KEY, TEST_VALUE);
        } catch (Exception e) {
            Assertions.fail("Exception thrown: " + e.getMessage());
        }
    }

    @Test
    public void testGet() {
        try {
            // Implement test for get method
            Mockito.when(redisClient.get(TEST_KEY)).thenReturn(TEST_VALUE);
            Optional<String> value = redisAccessor.get(TEST_KEY);
            Mockito.verify(redisClient, Mockito.times(1)).get(TEST_KEY);
            Assertions.assertEquals(TEST_VALUE, value.get());
        } catch (Exception e) {
            Assertions.fail("Exception thrown: " + e.getMessage());
        }
    }

    @Test
    public void testSetWithRetries() {
        try {
            // Implement test for set method with retries
            Mockito.when(redisClient.set(TEST_KEY, TEST_VALUE))
                    .thenThrow(new JedisConnectionException("Connection error"))
                    .thenThrow(new JedisConnectionException("Connection error"))
                    .thenReturn("OK");
            redisAccessor.set(TEST_KEY, TEST_VALUE);
            Mockito.verify(redisClient, Mockito.times(3)).set(TEST_KEY, TEST_VALUE);
        } catch (Exception e) {
            Assertions.fail("Exception thrown: " + e.getMessage());
        }
    }

    @Test
    public void testGetWithRetries() {
        try {
            // Implement test for get method with retries
            Mockito.when(redisClient.get(TEST_KEY))
                    .thenThrow(new JedisConnectionException("Connection error"))
                    .thenThrow(new JedisConnectionException("Connection error"))
                    .thenReturn(TEST_VALUE);
            Optional<String> value = redisAccessor.get(TEST_KEY);
            Mockito.verify(redisClient, Mockito.times(3)).get(TEST_KEY);
            assert value.orElse("").equals(TEST_VALUE);
        } catch (Exception e) {
            Assertions.fail("Exception thrown: " + e.getMessage());
        }
    }

    @Test
    public void testSetWithRetriesExhausted() {
        // Implement test for set method with retries exhausted
        Mockito.when(redisClient.set(TEST_KEY, TEST_VALUE))
               .thenThrow(new JedisConnectionException("Connection error"))
               .thenThrow(new JedisConnectionException("Connection error"))
               .thenThrow(new JedisConnectionException("Connection error"));
        try {
            redisAccessor.set(TEST_KEY, TEST_VALUE);
            assert false; // Should not reach here
        } catch (Exception e) {
            assert e.getMessage().contains("Failed to set key in Redis after");
        }
        Mockito.verify(redisClient, Mockito.times(3)).set(TEST_KEY, TEST_VALUE);
    }

    @Test
    public void testGetWithRetriesExhausted() {
        // Implement test for get method with retries exhausted
        Mockito.when(redisClient.get(TEST_KEY))
               .thenThrow(new JedisConnectionException("Connection error"))
               .thenThrow(new JedisConnectionException("Connection error"))
               .thenThrow(new JedisConnectionException("Connection error"));
        try {
            redisAccessor.get(TEST_KEY);
            assert false; // Should not reach here
        } catch (Exception e) {
            assert e.getMessage().contains("Failed to get key from Redis after");
        }
        Mockito.verify(redisClient, Mockito.times(3)).get(TEST_KEY);
    }

    @Test
    public void testGetWithRetriesInterrupted() {
        // Implement test for get method with retries interrupted
        Mockito.when(redisClient.get(TEST_KEY))
                .thenThrow(new JedisConnectionException("Connection error"))
                .thenThrow(new JedisConnectionException("Connection error"))
                .thenAnswer(invocation -> {
                    Thread.currentThread().interrupt(); // interrupt during second attempt's backoff
                    throw new JedisConnectionException("Connection error");
                });
        try {
            redisAccessor.get(TEST_KEY);
            assert false; // Should not reach here
        } catch (InterruptedException e) {
            assert true;
        } catch (Exception e) {
            Assertions.fail(e);
        }
        Mockito.verify(redisClient, Mockito.times(3)).get(TEST_KEY);
    }

    @Test
    public void testSetWithRetriesInterrupted() {
        // Implement test for set method with retries interrupted
        Mockito.when(redisClient.set(TEST_KEY, TEST_VALUE))
                .thenThrow(new JedisConnectionException("Connection error"))
                .thenThrow(new JedisConnectionException("Connection error"))
                .thenAnswer(invocation -> {
                    Thread.currentThread().interrupt(); // interrupt during second attempt's backoff
                    throw new JedisConnectionException("Connection error");
                });
        try {
            redisAccessor.set(TEST_KEY, TEST_VALUE);
            assert false; // Should not reach here
        } catch (InterruptedException e) {
            assert true;
        } catch (Exception e) {
            Assertions.fail(e);
        }
        Mockito.verify(redisClient, Mockito.times(3)).set(TEST_KEY, TEST_VALUE);
    }

    

}
