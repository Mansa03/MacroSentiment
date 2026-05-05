package com.mirror.queries.v1;

import com.mirror.queries.TransactionResults;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.mockito.Mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mirror.models.v1.ImmutableRawAPINews;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;

import java.util.List;

import org.mockito.Mockito;

@ExtendWith(MockitoExtension.class)
@Slf4j
public class BatchRawAPINewsTest {
    private final String TEST_URL = "TEST_URL";
    private final String TEST_SOURCE_ID = "TEST_SOURCE_ID";
    private final String TEST_SOURCE_NAME = "TEST_SOURCE_NAME";
    private final String TEST_AUTHOR = "TEST_AUTHOR";
    private final String TEST_TITLE = "TEST_TITLE";
    private final String TEST_DESCRIPTION = "TEST_DESCRIPTION";
    private final String TEST_URL_TO_IMAGE = "TEST_URL_TO_IMAGE";
    private final String TEST_PUBLISHED_AT = "TEST_PUBLISHED_AT";
    private final String TEST_CONTENT = "TEST_CONTENT";
    private final int[] SUCCESS = {1,1,1,1,1,1,1,1,-2,1,1,1,1,1,1,1,-2,1,1,1};
    private final int[] FAILED_EXECUTE = {1,1,1,1,1,1,-3,1,1,1,-3,1,1,-3,-3,1,1,1,1,1};
    private final int[] SKIPPED_EXECUTE = {1,1,1,1,1,1,0,1,1,1,0,1,1,0,0,1,1,1,1,1};
    private final int[] SKIPPED_AND_FAILED = {1,1,1,1,1,1,-3,1,1,1,-3,1,1,0,0,1,1,1,1,1};


    @Mock
    private Connection mockConn;
    @Mock
    private PreparedStatement mockPstmt;
    @Mock
    BatchUpdateException exception;
    private static final BatchRawAPINews func = new BatchRawAPINews();

    @Test
    public void testNonRetryableBatchFailures() {
        List<ImmutableRawAPINews> transactions = createNewsBatches(1, 20);
        try{
        Mockito.when(mockConn.prepareStatement(anyString())).thenReturn(mockPstmt);
        Mockito.doNothing().when(mockPstmt).setString(Mockito.anyInt(), Mockito.anyString());
        Mockito.when(mockPstmt.executeBatch()).thenThrow(exception);
        Mockito.when(exception.getSQLState()).thenReturn("22","23", "42", "28");
        Mockito.when(exception.getNextException()).thenReturn(exception);
        Mockito.when(exception.getUpdateCounts()).thenReturn(FAILED_EXECUTE);
        TransactionResults<ImmutableRawAPINews> transactionResults= func.apply(mockConn, transactions);
        assert(transactionResults.failedTransactions().UnRetryableTransactions().size() == 4);
        } catch (SQLException e) {
            Assertions.fail("test failed due to exception", e);

        }
    }

    @Test
    public void testRetryableBatchFailures() {
        List<ImmutableRawAPINews> transactions = createNewsBatches(1, 20);
        try{
            Mockito.when(mockConn.prepareStatement(anyString())).thenReturn(mockPstmt);
            Mockito.doNothing().when(mockPstmt).setString(Mockito.anyInt(), Mockito.anyString());
            Mockito.when(mockPstmt.executeBatch()).thenThrow(exception);
            Mockito.when(exception.getSQLState()).thenReturn( "08000", "08001", "08003", "08004");
            Mockito.when(exception.getNextException()).thenReturn(exception);
            Mockito.when(exception.getUpdateCounts()).thenReturn(FAILED_EXECUTE);
            TransactionResults<ImmutableRawAPINews> transactionResults= func.apply(mockConn, transactions);
            assert(transactionResults.failedTransactions().RetryableTransactions().size() == 4);
        } catch (SQLException e) {
            Assertions.fail("test failed due to exception", e);

        }
    }

    @Test
    public void testSkipped() {
        List<ImmutableRawAPINews> transactions = createNewsBatches(1, 20);
        try{
            Mockito.when(mockConn.prepareStatement(anyString())).thenReturn(mockPstmt);
            Mockito.doNothing().when(mockPstmt).setString(Mockito.anyInt(), Mockito.anyString());
            Mockito.when(mockPstmt.executeBatch()).thenReturn(SKIPPED_EXECUTE);
            Mockito.verify(exception, Mockito.never()).getSQLState();
            Mockito.verify(exception, Mockito.never()).getNextException();
            TransactionResults<ImmutableRawAPINews> transactionResults= func.apply(mockConn, transactions);
            assert(transactionResults.skippedTransactions().size() == 4);
        } catch (SQLException e) {
            Assertions.fail("test failed due to exception", e);

        }
    }

    @Test
    public void testSuccess() {
        List<ImmutableRawAPINews> transactions = createNewsBatches(1, 20);
        try{
            Mockito.when(mockConn.prepareStatement(anyString())).thenReturn(mockPstmt);
            Mockito.doNothing().when(mockPstmt).setString(Mockito.anyInt(), Mockito.anyString());
            Mockito.when(mockPstmt.executeBatch()).thenReturn(SUCCESS);
            Mockito.verify(exception, Mockito.never()).getSQLState();
            Mockito.verify(exception, Mockito.never()).getNextException();
            TransactionResults<ImmutableRawAPINews> transactionResults= func.apply(mockConn, transactions);
            assert(transactionResults.skippedTransactions().isEmpty());
            assert(transactionResults.successfullTransactions().size() == SUCCESS.length);
        } catch (SQLException e) {
            Assertions.fail("test failed due to exception", e);
        }
    }

    @Test
    public void testSkippedAndFailed() {
        List<ImmutableRawAPINews> transactions = createNewsBatches(1, 20);
        try{
            Mockito.when(mockConn.prepareStatement(anyString())).thenReturn(mockPstmt);
            Mockito.doNothing().when(mockPstmt).setString(Mockito.anyInt(), Mockito.anyString());
            Mockito.when(mockPstmt.executeBatch()).thenThrow(exception);
            Mockito.when(exception.getSQLState()).thenReturn( "22", "23");
            Mockito.when(exception.getNextException()).thenReturn(exception);
            Mockito.when(exception.getUpdateCounts()).thenReturn(SKIPPED_AND_FAILED);
            TransactionResults<ImmutableRawAPINews> transactionResults= func.apply(mockConn, transactions);
            assert(transactionResults.failedTransactions().RetryableTransactions().isEmpty());
            assert(transactionResults.failedTransactions().UnRetryableTransactions().size() == 2);
            assert(transactionResults.skippedTransactions().size() == 2);
            assert(transactionResults.successfullTransactions().size() == 16);
        } catch (SQLException e) {
            Assertions.fail("test failed due to exception", e);

        }
    }

    @Test
    public void testHandleSQLException() {
        List<ImmutableRawAPINews> transactions = createNewsBatches(1, 20);
        try{
            Mockito.when(mockConn.prepareStatement(anyString())).thenReturn(mockPstmt);
            Mockito.doNothing().when(mockPstmt).setString(Mockito.anyInt(), Mockito.anyString());
            Mockito.when(mockPstmt.executeBatch()).thenThrow(new SQLException("Testing Error handling"));
            Mockito.verify(exception, Mockito.never()).getSQLState();
            Mockito.verify(exception, Mockito.never()).getNextException();
            TransactionResults<ImmutableRawAPINews> transactionResults= func.apply(mockConn, transactions);
            Assertions.fail("Should rethrow ie");
        } catch (SQLException e) {
            assert true;
        }
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
