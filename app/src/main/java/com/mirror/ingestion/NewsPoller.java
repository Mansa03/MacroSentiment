package com.mirror.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.net.URI;

import com.mirror.accessors.RedisAccessor;
import com.mirror.accessors.NewsAPIAccessor;
import com.mirror.models.v1.*;

import redis.clients.jedis.RedisClient;

import com.google.common.collect.ImmutableList;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@AllArgsConstructor
public class NewsPoller implements Callable<PollResults> {
    private final NewsAPIAccessor newsAPIAccessor;
    private final List<String> keywords;
    private final RedisAccessor redisAccessor;
    private final ObjectMapper objectMapper;
    @Override
    public PollResults call() throws InterruptedException{
        try{
            String fromTimeStamp = redisAccessor.get(keywords.toString()).orElse(null);
            String nextFromTimeStamp = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(120).toString();
            if (fromTimeStamp == null) {
                fromTimeStamp = LocalDate.now(ZoneOffset.UTC)
                      .minusDays(1)
                      .toString();
            }
            ResponseResult newsResponse = newsAPIAccessor.getNews(keywords, fromTimeStamp);
            URI uri = newsResponse.uri();
            Optional<String> newsData = newsResponse.responseBody();
            NewsAPIStatusCodes status = newsResponse.statusCode();
            if (status == NewsAPIStatusCodes.OK) {
                if (newsData.isPresent()) {
                    // Process the news data
                    try {
                        ImmutableAPINewsResponse response = objectMapper.readValue(newsData.get(), ImmutableAPINewsResponse.class);
                        return new PollResults(ImmutableList.copyOf(response.articles()), null, nextFromTimeStamp, PollStatus.SUCCESS);
                    } catch (Exception e) {
                        log.error("Error processing news data: {} for keywords: {}, from timestamp: {}", e.getMessage(), keywords, fromTimeStamp);
                        return new PollResults(ImmutableList.of(), newsData.get(), fromTimeStamp, PollStatus.PARSE_FAILURE);
                    }
                }
            }
            log.error("Failed to retrieve news data for keywords: %s, from timestamp: %s".formatted(keywords, fromTimeStamp));
            return new PollResults(ImmutableList.of(), newsData.orElse(null), fromTimeStamp, PollStatus.FETCH_FAILURE);
        } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Thread interrupted while fetching news, shuttingdown");
                throw ie;
        }
    }

    public String getKeywords() {
        return String.join(",", keywords);

    }
}