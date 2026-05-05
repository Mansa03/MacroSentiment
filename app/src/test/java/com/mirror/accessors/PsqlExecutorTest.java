package com.mirror.accessors;

import com.mirror.queries.TransactionResults;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.Mock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mirror.models.v1.ImmutableRawAPINews;
import com.mirror.queries.v1.BatchRawAPINews;
import com.mirror.queries.QueryBiFunction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.mockito.Mockito;


@ExtendWith(MockitoExtension.class)
public class PsqlExecutorTest {
    @Mock
    private static Connection mockConn;
    @Mock
    private PreparedStatement mockPreparedStatement;
    @Mock
    private static RedisAccessor redisClient;
    @Mock
    private static QueryBiFunction<Connection, List<ImmutableRawAPINews>, TransactionResults<ImmutableRawAPINews>> BatchInsert;
    private static PsqlExecutor<ImmutableRawAPINews> psqlExecutor;

    private final String TEST_URL = "TEST_URL";
    private final String TEST_SOURCE_ID = "TEST_SOURCE_ID";
    private final String TEST_SOURCE_NAME = "TEST_SOURCE_NAME";
    private final String TEST_AUTHOR = "TEST_AUTHOR";
    private final String TEST_TITLE = "TEST_TITLE";
    private final String TEST_DESCRIPTION = "TEST_DESCRIPTION";
    private final String TEST_URL_TO_IMAGE = "TEST_URL_TO_IMAGE";
    private final String TEST_PUBLISHED_AT = "TEST_PUBLISHED_AT";
    private final String TEST_CONTENT = "TEST_CONTENT";

    private static final String KEYWORDS = "Gift,Lift,Rift,Sift,Sith";
    private static final String NEXT_TIMESTAMP = "1893893191";

    @BeforeAll
    public static void setup() {
        MockitoAnnotations.openMocks(PsqlExecutorTest.class);
        BatchInsert = new BatchRawAPINews();
        psqlExecutor = new PsqlExecutor<ImmutableRawAPINews>(mockConn, redisClient, BatchInsert);
    }




    public List<ImmutableRawAPINews> createNewsBatches(int numOfBatches, int batchSize) {
        List<ImmutableRawAPINews> newsList = new ArrayList<>();
        for (int i = 0; i < numOfBatches * batchSize; i++) {
            newsList.add(ImmutableRawAPINews.builder()
                .url(TEST_URL + i)
                .sourceId(TEST_SOURCE_ID + i)
                .sourceName(TEST_SOURCE_NAME + i)
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


    
}
