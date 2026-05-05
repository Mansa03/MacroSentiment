package com.mirror.dagger;

import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

import javax.inject.Named;

@Module
public class EnvironmentModule {
    public static final String NEWS_API_KEY = "NEWS_API_KEY";
    public static final String PSQL_CONNECTION_URL = "PSQL_CONNECTION_URL";
    public static final String REDIS_URL = "REDIS_URL";
    public static final String PSQL_USERNAME = "PSQL_USERNAME";
    public static final String PSQL_PASSWORD = "PSQL_PASSWORD";
    public static final String START_FROM = "START_FROM";
    public static final String REDIS_HOST = "REDIS_HOST";
    public static final String REDIS_PORT = "REDIS_PORT";


    @Provides
    @Singleton
    @Named(NEWS_API_KEY)
    public String provideNewsApiKey() {
        return System.getenv(NEWS_API_KEY);
    }

    @Provides
    @Singleton
    @Named(PSQL_CONNECTION_URL)
    public String providePsqlConnectionUrl() {
        return System.getenv(PSQL_CONNECTION_URL);
    }

    @Provides
    @Singleton
    @Named(PSQL_USERNAME)
    public String providePsqlUsername() {
        return System.getenv(PSQL_USERNAME);
    }

    @Provides
    @Singleton
    @Named(PSQL_PASSWORD)
    public String providePsqlPassword() {
        return System.getenv(PSQL_PASSWORD);
    }

    

    @Provides
    @Singleton
    @Named(REDIS_HOST)
    public String provideRedisHost() {
        return System.getenv(REDIS_HOST);
    }

    @Provides
    @Singleton
    @Named(REDIS_PORT)
    public int provideRedisPort() {
        return Integer.parseInt(System.getenv(REDIS_PORT));
    }



    @Provides
    @Singleton
    @Named(REDIS_URL)
    public String provideRedisUrl() {
        return System.getenv(REDIS_URL);
    }

    @Provides
    @Singleton
    @Named(START_FROM)
    public String provideStartFrom() {
        return "2026-01-01T00:00:00Z";
    }

}
