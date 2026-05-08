package com.mirror;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.mirror.ingestion.ResponseResult;
import com.mirror.models.v1.ImmutableRawAPINews;
import com.mirror.models.v1.ImmutableSource;
import com.mirror.models.v1.NewsAPIStatusCodes;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TestHelper {
    private static final String TEST_URL = "TEST_URL";
    private static final String TEST_SOURCE_ID = "TEST_SOURCE_ID";
    private static final String TEST_SOURCE_NAME = "TEST_SOURCE_NAME";
    private static final String TEST_AUTHOR = "TEST_AUTHOR";
    private static final String TEST_TITLE = "TEST_TITLE";
    private static final String TEST_DESCRIPTION = "TEST_DESCRIPTION";
    private static final String TEST_URL_TO_IMAGE = "TEST_URL_TO_IMAGE";
    private static final String TEST_PUBLISHED_AT = "TEST_PUBLISHED_AT";
    private static final String TEST_CONTENT = "TEST_CONTENT";

    private static final String SOURCE = "source";
    private static final String ID = "id";
    private static final String NAME = "name";

    public static List<ImmutableRawAPINews> createNewsBatches(int numOfBatches, int batchSize) {
        List<ImmutableRawAPINews> newsList = new ArrayList<>();
        for (int i = 0; i < numOfBatches * batchSize; i++) {
            newsList.add(ImmutableRawAPINews.builder()
                    .url(TEST_URL)
                    .source(ImmutableSource.builder()
                            .id(TEST_SOURCE_ID + i)
                            .name(TEST_SOURCE_NAME + i)
                            .build())
                    .author(TEST_AUTHOR + i)
                    .title(TEST_TITLE + i)
                    .description(TEST_DESCRIPTION + i)
                    .urlToImage(TEST_URL_TO_IMAGE + i)
                    .publishedAt(TEST_PUBLISHED_AT + i)
                    .content(TEST_CONTENT + i)
                    .build());
        }
        return newsList;
    }

    public static ImmutableList<ImmutableRawAPINews> createNewsList(int numOfRecords) {
        List<ImmutableRawAPINews> newsList = new ArrayList<>();
        for (int i = 0; i < numOfRecords; i++) {
            newsList.add(ImmutableRawAPINews.builder()
                    .url(TEST_URL)
                    .source(ImmutableSource.builder()
                            .id(TEST_SOURCE_ID + i)
                            .name(TEST_SOURCE_NAME + i)
                            .build())
                    .author(TEST_AUTHOR + i)
                    .title(TEST_TITLE + i)
                    .description(TEST_DESCRIPTION + i)
                    .urlToImage(TEST_URL_TO_IMAGE + i)
                    .publishedAt(TEST_PUBLISHED_AT + i)
                    .content(TEST_CONTENT + i)
                    .build());
        }
        return ImmutableList.copyOf(newsList);
    }

    public static ResponseResult createResponseResult(int numRecords, NewsAPIStatusCodes code, URI uri) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        int numOfbatches = numRecords >= 20 ? numRecords / 20 : 1;
        List<ImmutableRawAPINews> news = createNewsBatches(numOfbatches, 20);
        String newsAsString = mapper.writeValueAsString(news);
        StringBuilder builder = new StringBuilder();
        builder.append(""" 
                {"status": "%s", "totalResults": "%d", "articles": %s
                }""".formatted(code, numRecords, newsAsString));
        String responseBody = builder.toString();
        return new ResponseResult(uri, Optional.of(responseBody), code);

    }
}
