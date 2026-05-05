package com.mirror.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.net.URI;

import com.mirror.accessors.RedisAccessor;
import com.mirror.accessors.NewsAPIAccessor;
import com.mirror.models.v1.ImmutableAPINewsResponse;
import com.mirror.models.v1.ImmutableRawAPINews;
import com.mirror.models.v1.NewsAPIStatusCodes;

import com.mirror.models.v1.PollStatus;
import redis.clients.jedis.RedisClient;

import com.google.common.collect.ImmutableList;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@AllArgsConstructor
public class NewsPoller implements Callable<PollResults> {
    public static final String AUTHORED_BY_NEWS_POLLER = "Authored by NewsPoller";
    private final NewsAPIAccessor newsAPIAccessor;
    private final List<String> keywords;
    private final RedisAccessor redisAccessor;
    private final ObjectMapper objectMapper;
    @Override
    public PollResults call(){
        try{
            String fromTimeStamp = redisAccessor.get(keywords.toString()).orElse(null);
            String nextFromTimeStamp = LocalDate.now(ZoneOffset.UTC).minus(120, ChronoUnit.SECONDS).toString();
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
                        ImmutableList<ImmutableRawAPINews> articles = response.articles();
                        return new PollResults(articles, null, nextFromTimeStamp, PollStatus.SUCCESS);
                    } catch (Exception e) {
                        log.error("Error processing news data: {} for keywords: {}, from timestamp: {}", e.getMessage(), keywords, fromTimeStamp);
                        return new PollResults(ImmutableList.of(), newsData.get(), fromTimeStamp, PollStatus.PARSE_FAILURE);
                    }
                }
            }
            log.error("Failed to retrieve news data for keywords: {}, from timestamp: {}".formatted(keywords, fromTimeStamp));
            return new PollResults(ImmutableList.of(), null, fromTimeStamp, PollStatus.FETCH_FAILURE);
        } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Thread interrupted while fetching news, shuttingdown");
        }
        return new PollResults(ImmutableList.of(), null, null, PollStatus.FETCH_FAILURE);
    }

    public String getKeywords() {
        return String.join(",", keywords);

    }
}