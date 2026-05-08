package com.mirror.accessors;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;

import java.lang.InterruptedException;

import com.google.common.collect.Lists;
import com.mirror.queries.FailedTransactions;
import com.mirror.queries.QueryBiFunction;
import com.mirror.queries.TransactionResults;

import lombok.extern.slf4j.Slf4j;
import lombok.NonNull;
import lombok.AllArgsConstructor;

import javax.sql.DataSource;

@Slf4j
@AllArgsConstructor
public class PsqlExecutor<T> {

    private static final int BATCH_SIZE = 500;
    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 1000L;

    @NonNull private final DataSource dataSource;       // not Connection
    @NonNull private final RedisAccessor redisClient;
    @NonNull private final QueryBiFunction<Connection, List<T>, TransactionResults<T>> batchInsert;

    public TransactionResults<T> applyBatchInsert(
            List<T> items,
            String keywords,
            String nextTimeStamp) throws InterruptedException {

        List<T> succeeded = new ArrayList<>();
        List<T> skipped   = new ArrayList<>();
        List<T> failed    = new ArrayList<>();

        for (List<T> batch : Lists.partition(items, BATCH_SIZE)) {
            processBatch(batch, succeeded, skipped, failed);
        }

        if (!succeeded.isEmpty()) {
            redisClient.set(keywords, nextTimeStamp);
        }

        return new TransactionResults<>(
                succeeded,
                skipped,
                new FailedTransactions<>(List.of(), failed)
        );
    }

    private void processBatch(
            List<T> batch,
            List<T> succeeded,
            List<T> skipped,
            List<T> failed) throws InterruptedException {

        List<T> toProcess = batch;
        int attempts = 0;

        while (attempts < MAX_RETRIES) {
            try (Connection conn = dataSource.getConnection())  {
                TransactionResults<T> results = batchInsert.apply(conn, toProcess);

                // only commit if something succeeded
                if (!results.successfullTransactions().isEmpty()) {
                    conn.commit();
                }
                // no rollback needed if nothing succeeded — connection is clean

                succeeded.addAll(results.successfullTransactions());
                skipped.addAll(results.skippedTransactions());
                failed.addAll(results.failedTransactions().UnRetryableTransactions());

                List<T> retryable = results.failedTransactions().RetryableTransactions();

                if (retryable.isEmpty()) {
                    return;  // batch done
                }

                toProcess = retryable;
                attempts++;

                if (attempts >= MAX_RETRIES) {
                    log.warn("Max retries reached, dead lettering {} rows", toProcess.size());
                    failed.addAll(toProcess);
                    return;
                }

                sleep(attempts);

            } catch (SQLException e) {
                attempts++;
                String state = e.getSQLState();
                log.error("Batch attempt {}/{} failed sqlstate={}", attempts, MAX_RETRIES, state);

                if (attempts >= MAX_RETRIES) {
                    log.error("Max retries exhausted, dead lettering {} rows", toProcess.size());
                    failed.addAll(toProcess);
                    return;
                }

                // connection borrowed via try-with-resources
                // rollback + close handled automatically on exception
                sleep(attempts);

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("PsqlExecutor interrupted");
                throw ie;
            }
        }
    }

    private void sleep(int attempt) throws InterruptedException {
        try {
            Thread.sleep(BASE_DELAY_MS * (1L << attempt));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw ie;
        }
    }
}