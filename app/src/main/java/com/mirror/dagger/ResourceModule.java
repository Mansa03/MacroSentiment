package com.mirror.dagger;

import java.io.InputStream;
import java.net.http.HttpClient;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

import javax.inject.Named;

import lombok.NonNull;

import java.sql.DriverManager;
import java.time.Duration;
import java.sql.Connection;

import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.RedisClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Module
public class ResourceModule {
    public static final String HTTP_CLIENT = "HTTP_CLIENT";
    public static final String PSQL_CONNECTION = "PSQLConnection";
    public static final String PSQL_CONNECTION_POOL = "PSQL_CONNECTION_POOL";
    public static final String JSON_CONFIG_PATH = "configs/keys.json";
    public static final String MARKET_KEYWORDS = "market_keywords";
    public static final String STOCK_TICKER_KEYWORDS = "stock_ticker_keywords";

    @Provides
    @Named(HTTP_CLIENT)
    public HttpClient provideHttpClient() {
        // Return an instance of your HTTP client here
        return HttpClient.newHttpClient(); // Placeholder, replace with actual HTTP client instance
    }

    @Provides
    @Singleton
    public HikariConfig provideHikariConfig(@NonNull @Named(EnvironmentModule.PSQL_CONNECTION_URL) String psqlUrl) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(psqlUrl);
        config.setAutoCommit(false);
        config.setUsername("user");
        config.setPassword("password");
        config.setMaximumPoolSize(20);
        config.setIdleTimeout(Duration.ofSeconds(10).toMillis());
        config.setConnectionTimeout(Duration.ofSeconds(10).toMillis());
        return config;
    }

    @Provides
    @Singleton
    public HikariDataSource providesDataSource(HikariConfig config) {
        return new HikariDataSource(config);
    }



    @Provides
    public JsonNode providesJsonConfig(@NonNull ObjectMapper mappper) {
        try {
            InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream(JSON_CONFIG_PATH);
            if (inputStream == null) {
                throw new RuntimeException("Config file not found: " + JSON_CONFIG_PATH);
            }
            return mappper.readTree(inputStream);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read JSON config", e);
        }
    }

    @Provides
    public Jedis provideJedisClient(@NonNull @Named(EnvironmentModule.REDIS_URL) String redisUrl) {
        return new Jedis(redisUrl);
    }

    @Provides RedisClient provideRedisClient(@NonNull @Named(EnvironmentModule.REDIS_HOST) String redisHost, @Named(EnvironmentModule.REDIS_PORT) int redisPort, @NonNull ConnectionPoolConfig poolConfig) {
        return RedisClient.builder()
                .hostAndPort(redisHost, redisPort)
                .poolConfig(poolConfig)
                .build();
    }

    @Provides
    public ConnectionPoolConfig provideRedisConnectionPoolConfig() {
        ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();
        poolConfig.setMaxTotal(20);
        poolConfig.setMaxIdle(20);
        poolConfig.setMinIdle(0);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setNumTestsPerEvictionRun(-1);
        poolConfig.setTimeBetweenEvictionRuns(Duration.ofSeconds(30));
        poolConfig.setBlockWhenExhausted(true);
        poolConfig.setMaxWait(Duration.ofSeconds(10));
        return poolConfig;
    }

}
