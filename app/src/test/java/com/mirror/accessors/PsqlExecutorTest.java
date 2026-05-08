package com.mirror.accessors;

import com.google.common.collect.Lists;
import com.mirror.TestHelper;
import com.mirror.models.v1.ImmutableRawAPINews;
import com.mirror.queries.FailedTransactions;
import com.mirror.queries.QueryBiFunction;
import com.mirror.queries.TransactionResults;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;


@ExtendWith(MockitoExtension.class)
public class PsqlExecutorTest {
    @Mock
    HikariDataSource dataSourceMock;
    @Mock
    private Connection connMock;
    @Mock
    private RedisAccessor redisClientMock;
    @Mock
    private QueryBiFunction<Connection, List<ImmutableRawAPINews>, TransactionResults<ImmutableRawAPINews>> batchInsertMock;
    private PsqlExecutor<ImmutableRawAPINews> psqlExecutor;

    private static final String KEYWORDS = "Gift,Lift,Rift,Sift,Sith";
    private static final String NEXT_TIMESTAMP = "1893893191";

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        psqlExecutor = new PsqlExecutor<ImmutableRawAPINews>(dataSourceMock, redisClientMock, batchInsertMock);
    }

    @Test
    public void testSuccessfulBatch() {
        try {
            List<ImmutableRawAPINews> transactions = TestHelper.createNewsBatches(1, 100);
            List<ImmutableRawAPINews> successful = List.copyOf(transactions);
            List<ImmutableRawAPINews> skipped = List.of();
            List<ImmutableRawAPINews> retryable = List.of();
            List<ImmutableRawAPINews> unRetryable = List.of();
            FailedTransactions<ImmutableRawAPINews> failed = new FailedTransactions<>(retryable, unRetryable);
            TransactionResults<ImmutableRawAPINews> transactionResults = new TransactionResults<ImmutableRawAPINews>(successful, skipped, failed);
            Mockito.when(batchInsertMock.apply(connMock, transactions)).thenReturn(transactionResults);
            Mockito.when(dataSourceMock.getConnection()).thenReturn(connMock);
            Mockito.doNothing().when(connMock).commit();
            Mockito.doNothing().when(redisClientMock).set(any(), eq(NEXT_TIMESTAMP));
            TransactionResults<ImmutableRawAPINews> results = psqlExecutor.applyBatchInsert(transactions, KEYWORDS, NEXT_TIMESTAMP);
            Mockito.verify(connMock, Mockito.times(1)).commit();
            Mockito.verify(redisClientMock, Mockito.times(1)).set(any(), eq(NEXT_TIMESTAMP));
            Mockito.verify(connMock, Mockito.never()).rollback();
            Assertions.assertTrue(results.failedTransactions().UnRetryableTransactions().isEmpty());
            Assertions.assertTrue(results.failedTransactions().RetryableTransactions().isEmpty());
            Assertions.assertTrue(results.skippedTransactions().isEmpty());
            Assertions.assertEquals(100, results.successfullTransactions().size());
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    @Test
    public void testSuccssfulMultipleBatches() {
        try {
            List<ImmutableRawAPINews> transactions = TestHelper.createNewsBatches(3, 500);
            List<List<ImmutableRawAPINews>> successful = Lists.partition(transactions, 500);
            List<ImmutableRawAPINews> skipped = List.of();
            List<ImmutableRawAPINews> retryable = List.of();
            List<ImmutableRawAPINews> unRetryable = List.of();
            FailedTransactions<ImmutableRawAPINews> failed = new FailedTransactions<>(retryable, unRetryable);
            Mockito.doReturn(new TransactionResults<ImmutableRawAPINews>(successful.get(0), skipped, failed))
                    .doReturn(new TransactionResults<ImmutableRawAPINews>(successful.get(1), skipped, failed))
                    .doReturn(new TransactionResults<ImmutableRawAPINews>(successful.get(2), skipped, failed))
                    .when(batchInsertMock).apply(eq(connMock), any());
            Mockito.when(dataSourceMock.getConnection()).thenReturn(connMock);
            Mockito.doNothing().when(connMock).commit();
            Mockito.doNothing().when(redisClientMock).set(any(), eq(NEXT_TIMESTAMP));
            TransactionResults<ImmutableRawAPINews> results = psqlExecutor.applyBatchInsert(transactions, KEYWORDS, NEXT_TIMESTAMP);
            Mockito.verify(connMock, Mockito.times(3)).commit();
            Mockito.verify(redisClientMock, Mockito.times(1)).set(any(), eq(NEXT_TIMESTAMP));
            Mockito.verify(connMock, Mockito.never()).rollback();
            Assertions.assertTrue(results.failedTransactions().UnRetryableTransactions().isEmpty());
            Assertions.assertTrue(results.failedTransactions().RetryableTransactions().isEmpty());
            Assertions.assertTrue(results.skippedTransactions().isEmpty());
            Assertions.assertEquals(1500, results.successfullTransactions().size());
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    @Test
    public void testPartialFailuresAndRetries() {
        try {

            List<ImmutableRawAPINews> transactions = TestHelper.createNewsBatches(2, 500);
            List<List<ImmutableRawAPINews>> batches = Lists.partition(transactions, 500);
            List<List<ImmutableRawAPINews>> successfulBatches = List.of(batches.get(0).subList(0, 250), batches.get(1).subList(0, 250));
            List<List<ImmutableRawAPINews>> failedBatches = List.of(batches.get(0).subList(250, 500), batches.get(1).subList(250, 500));
            List<ImmutableRawAPINews> skipped = List.of();
            List<ImmutableRawAPINews> retryable = List.of();
            List<ImmutableRawAPINews> unRetryable = List.of();
            FailedTransactions<ImmutableRawAPINews> failed = new FailedTransactions<>(retryable, unRetryable);
            Mockito.when(dataSourceMock.getConnection()).thenReturn(connMock);
            Mockito.when(batchInsertMock.apply(eq(connMock), any()))
                    .thenReturn(
                            new TransactionResults<>(successfulBatches.get(0),
                                    skipped,
                                    new FailedTransactions<>(failedBatches.get(0), unRetryable)))
                    .thenReturn(new TransactionResults<>(failedBatches.get(0),
                            skipped,
                            new FailedTransactions<>(retryable, unRetryable)))
                    .thenReturn(
                            new TransactionResults<>(successfulBatches.get(1),
                                    skipped,
                                    new FailedTransactions<>(failedBatches.get(1), unRetryable)))
                    .thenReturn(new TransactionResults<>(failedBatches.get(1),
                            skipped,
                            new FailedTransactions<>(retryable, unRetryable)));
            Mockito.when(dataSourceMock.getConnection()).thenReturn(connMock);
            Mockito.doNothing().when(connMock).commit();
            Mockito.doNothing().when(redisClientMock).set(any(), eq(NEXT_TIMESTAMP));
            TransactionResults<ImmutableRawAPINews> results = psqlExecutor.applyBatchInsert(transactions, KEYWORDS, NEXT_TIMESTAMP);
            Mockito.verify(connMock, Mockito.times(4)).commit();
            Mockito.verify(redisClientMock, Mockito.times(1)).set(any(), eq(NEXT_TIMESTAMP));
            Mockito.verify(connMock, Mockito.never()).rollback();
            Assertions.assertTrue(results.failedTransactions().UnRetryableTransactions().isEmpty());
            Assertions.assertTrue(results.failedTransactions().RetryableTransactions().isEmpty());
            Assertions.assertTrue(results.skippedTransactions().isEmpty());
            Assertions.assertEquals(1000, results.successfullTransactions().size(), "Expected %s and got %s".formatted(1000, results.successfullTransactions().size()));
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    @Test
    public void testRetriesExhausted() {
        try {
            List<ImmutableRawAPINews> transactions = TestHelper.createNewsBatches(1, 100);
            List<ImmutableRawAPINews> successful = List.of();
            List<ImmutableRawAPINews> skipped = List.of();
            List<ImmutableRawAPINews> retryable = List.copyOf(transactions);
            List<ImmutableRawAPINews> unRetryable = List.of();
            FailedTransactions<ImmutableRawAPINews> failed = new FailedTransactions<>(retryable, unRetryable);
            TransactionResults<ImmutableRawAPINews> transactionResults = new TransactionResults<ImmutableRawAPINews>(successful, skipped, failed);
            Mockito.when(dataSourceMock.getConnection()).thenReturn(connMock);
            Mockito.when(batchInsertMock.apply(connMock, transactions)).thenReturn(transactionResults);
            TransactionResults<ImmutableRawAPINews> results = psqlExecutor.applyBatchInsert(transactions, KEYWORDS, NEXT_TIMESTAMP);
            Mockito.verify(redisClientMock, Mockito.never()).set(any(), eq(NEXT_TIMESTAMP));
            Mockito.verify(connMock, Mockito.never()).rollback();
            Mockito.verify(connMock, Mockito.never()).commit();
            Mockito.verify(batchInsertMock, Mockito.times(3)).apply(any(), any());
            Assertions.assertEquals(100, results.failedTransactions().UnRetryableTransactions().size());
            Assertions.assertTrue(results.failedTransactions().RetryableTransactions().isEmpty());
            Assertions.assertTrue(results.skippedTransactions().isEmpty());
            Assertions.assertTrue(results.successfullTransactions().isEmpty());
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    @Test
    public void testSQLExceptionRetriesExhausted() {
        try {
            List<ImmutableRawAPINews> transactions = TestHelper.createNewsBatches(1, 100);
            List<ImmutableRawAPINews> successful = List.of();
            List<ImmutableRawAPINews> skipped = List.of();
            List<ImmutableRawAPINews> retryable = List.copyOf(transactions);
            List<ImmutableRawAPINews> unRetryable = List.of();
            FailedTransactions<ImmutableRawAPINews> failed = new FailedTransactions<>(retryable, unRetryable);
            TransactionResults<ImmutableRawAPINews> transactionResults = new TransactionResults<ImmutableRawAPINews>(successful, skipped, failed);
            Mockito.when(dataSourceMock.getConnection()).thenReturn(connMock);
            Mockito.when(batchInsertMock.apply(connMock, transactions)).thenReturn(transactionResults).thenReturn(transactionResults).thenThrow(new SQLException("SQL Exception"));
            TransactionResults<ImmutableRawAPINews> results = psqlExecutor.applyBatchInsert(transactions, KEYWORDS, NEXT_TIMESTAMP);
            Mockito.verify(redisClientMock, Mockito.never()).set(any(), eq(NEXT_TIMESTAMP));
            Mockito.verify(connMock, Mockito.never()).rollback();
            Mockito.verify(connMock, Mockito.never()).commit();
            Mockito.verify(batchInsertMock, Mockito.times(3)).apply(any(), any());
            Assertions.assertEquals(100, results.failedTransactions().UnRetryableTransactions().size());
            Assertions.assertTrue(results.failedTransactions().RetryableTransactions().isEmpty());
            Assertions.assertTrue(results.skippedTransactions().isEmpty());
            Assertions.assertTrue(results.successfullTransactions().isEmpty());
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    @Test
    public void testSQLExceptionErrorHandling() {
        try {
            List<ImmutableRawAPINews> transactions = TestHelper.createNewsBatches(1, 100);
            List<ImmutableRawAPINews> successful = List.copyOf(transactions);
            List<ImmutableRawAPINews> skipped = List.of();
            List<ImmutableRawAPINews> retryable = List.of();
            List<ImmutableRawAPINews> unRetryable = List.of();
            FailedTransactions<ImmutableRawAPINews> failed = new FailedTransactions<>(retryable, unRetryable);
            TransactionResults<ImmutableRawAPINews> transactionResults = new TransactionResults<ImmutableRawAPINews>(successful, skipped, failed);
            Mockito.when(dataSourceMock.getConnection()).thenReturn(connMock);
            Mockito.when(batchInsertMock.apply(connMock, transactions)).thenThrow(new SQLException("SQL Exception")).thenReturn(transactionResults);
            TransactionResults<ImmutableRawAPINews> results = psqlExecutor.applyBatchInsert(transactions, KEYWORDS, NEXT_TIMESTAMP);
            Mockito.verify(connMock, Mockito.times(1)).commit();
            Mockito.verify(redisClientMock, Mockito.times(1)).set(any(), eq(NEXT_TIMESTAMP));
            Mockito.verify(connMock, Mockito.never()).rollback();
            Assertions.assertTrue(results.failedTransactions().UnRetryableTransactions().isEmpty());
            Assertions.assertTrue(results.failedTransactions().RetryableTransactions().isEmpty());
            Assertions.assertTrue(results.skippedTransactions().isEmpty());
            Assertions.assertEquals(100, results.successfullTransactions().size());
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    @Test
    public void testSQLExceptionErrorInterruptedHandling() {
        try {
            List<ImmutableRawAPINews> transactions = TestHelper.createNewsBatches(1, 100);
            List<ImmutableRawAPINews> successful = List.copyOf(transactions);
            List<ImmutableRawAPINews> skipped = List.of();
            List<ImmutableRawAPINews> retryable = List.of();
            List<ImmutableRawAPINews> unRetryable = List.of();
            FailedTransactions<ImmutableRawAPINews> failed = new FailedTransactions<>(retryable, unRetryable);
            TransactionResults<ImmutableRawAPINews> transactionResults = new TransactionResults<ImmutableRawAPINews>(successful, skipped, failed);
            Mockito.when(dataSourceMock.getConnection()).thenReturn(connMock);
            Mockito.when(batchInsertMock.apply(connMock, transactions)).thenThrow(new SQLException("SQL Exception")).thenReturn(transactionResults);
            TransactionResults<ImmutableRawAPINews> results = psqlExecutor.applyBatchInsert(transactions, KEYWORDS, NEXT_TIMESTAMP);
            Mockito.verify(connMock, Mockito.times(1)).commit();
            Mockito.verify(redisClientMock, Mockito.times(1)).set(any(), eq(NEXT_TIMESTAMP));
            Mockito.verify(connMock, Mockito.never()).rollback();
            Assertions.assertTrue(results.failedTransactions().UnRetryableTransactions().isEmpty());
            Assertions.assertTrue(results.failedTransactions().RetryableTransactions().isEmpty());
            Assertions.assertTrue(results.skippedTransactions().isEmpty());
            Assertions.assertEquals(100, results.successfullTransactions().size());
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

}
