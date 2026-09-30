package com.mirror.dagger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.Lists;
import com.mirror.NewsIngestionService;
import com.mirror.accessors.NewsAPIAccessor;
import com.mirror.accessors.PsqlExecutor;
import com.mirror.accessors.RedisAccessor;
import com.mirror.ingestion.NewsPoller;
import com.mirror.ingestion.PollingManager;
import com.mirror.models.v1.ImmutableRawAPINews;
import com.mirror.queries.QueryBiFunction;
import com.mirror.queries.TransactionResults;
import com.mirror.queries.v1.BatchRawAPINews;
import com.zaxxer.hikari.HikariDataSource;
import dagger.Module;
import dagger.Provides;
import lombok.NonNull;
import redis.clients.jedis.RedisClient;

import javax.inject.Named;
import javax.inject.Singleton;
import java.net.http.HttpClient;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Module
public class ApplicationModule {
    public static final String PSQL_BATCH_INSERT_FUNCTION = "PSQL_BATCH_INSERT_FUNCTION";
    public static final String PSQL_BATCH_INSERT_FALLBACK = "PSQL_BATCH_INSERT_FALLBACK";
    public static final String MARKET_KEYWORDS_LIST = "MARKET_KEYWORDS_LIST";
    public static final String STOCK_TICKER_KEYWORDS_LIST = "STOCK_TICKER_KEYWORDS_LIST";

    @Provides
    @Singleton
    public ObjectMapper provideObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Provides
    @Singleton
    public NewsAPIAccessor provideNewsAPIAccessor(@NonNull @Named(EnvironmentModule.NEWS_API_KEY) String newsApiKey, @NonNull @Named(ResourceModule.HTTP_CLIENT) HttpClient httpClient) {
        return new NewsAPIAccessor(newsApiKey, httpClient);
    }

    @Provides
    @Singleton
    public PsqlExecutor<ImmutableRawAPINews> providePsqlAccessor(HikariDataSource dataSource, @NonNull RedisAccessor redisClient, @NonNull @Named(PSQL_BATCH_INSERT_FUNCTION) QueryBiFunction<Connection, List<ImmutableRawAPINews>, TransactionResults<ImmutableRawAPINews>> batchInsertFunction) {
        return new PsqlExecutor<ImmutableRawAPINews>(dataSource, redisClient, batchInsertFunction);
    }

    @Provides
    @Named(PSQL_BATCH_INSERT_FUNCTION)
    public QueryBiFunction<Connection, List<ImmutableRawAPINews>, TransactionResults<ImmutableRawAPINews>> providePsqlBatchInsertFunction() {
        return new BatchRawAPINews();
    }


    @Provides
    public PollingManager providePollingManager(@NonNull ScheduledExecutorService executorService, @NonNull List<NewsPoller> newsPollers, @NonNull PsqlExecutor<ImmutableRawAPINews> psqlExecutor) {
        return new PollingManager(executorService, newsPollers, psqlExecutor);
    }

    @Provides
    @Singleton
    public NewsIngestionService provideNewsIngestionService(@NonNull PollingManager pollingManager) {
        return new NewsIngestionService(pollingManager);
    }

    @Provides
    @Singleton
    public ScheduledExecutorService provideScheduledExecutorService() {
        return java.util.concurrent.Executors.newScheduledThreadPool(5);
    }

    @Provides
    @Singleton
    @Named(MARKET_KEYWORDS_LIST)
    public List<String> provideMarketKeywords(@NonNull JsonNode jsonConfig) {
        JsonNode node = jsonConfig.get("market_keywords");
        return StreamSupport.stream(node.spliterator(), false)
                .map(JsonNode::textValue)
                .collect(Collectors.toList());
    }

    @Provides
    @Singleton
    @Named(STOCK_TICKER_KEYWORDS_LIST)
    public List<String> provideStockTickerKeywords(@NonNull JsonNode jsonConfig) {
        JsonNode node = jsonConfig.get("stock_ticker_keywords");
        return StreamSupport.stream(node.spliterator(), false)
                .map(JsonNode::textValue)
                .collect(Collectors.toList());
    }

    @Provides
    @Singleton
    public RedisAccessor provideRedisAccessor(@NonNull RedisClient redisClient) {
        return new RedisAccessor(redisClient);
    }

    @Provides
    @Singleton
    public List<NewsPoller> provideNewsPollers(@NonNull NewsAPIAccessor newsAPIAccessor, @NonNull @Named(MARKET_KEYWORDS_LIST) List<String> marketKeywords, @NonNull @Named(STOCK_TICKER_KEYWORDS_LIST) List<String> stockTickerKeywords, @NonNull RedisAccessor redisAccessor, @NonNull ObjectMapper objectMapper) {
        List<List<String>> marketGroups = Lists.partition(marketKeywords, 4);
        List<List<String>> stockTickerGroups = Lists.partition(stockTickerKeywords, 4);
        List<NewsPoller> newsPollers = new ArrayList<>();
        for (List<String> marketGroup : marketGroups) {
            newsPollers.add(new NewsPoller(newsAPIAccessor, marketGroup, redisAccessor, objectMapper));
        }
        for (List<String> stockTickerGroup : stockTickerGroups) {
            newsPollers.add(new NewsPoller(newsAPIAccessor, stockTickerGroup, redisAccessor, objectMapper));
        }
        return newsPollers;
    }


}