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

@Slf4j
@AllArgsConstructor
public class PsqlExecutor<T> {
    private static final int BATCH_SIZE = 500;
    private static final int MAX_RETRIES = 3;
    @NonNull
    private final Connection conn;
    @NonNull
    private final RedisAccessor redisClient;
    @NonNull
    private final QueryBiFunction<Connection, List<T>, TransactionResults<T>> batchInsert;



    public TransactionResults<T> applyBatchInsert(List<T> items, String keywords, String nextTimeStamp) throws InterruptedException{
        List<T> successfulTransactions = new ArrayList<>();
        List<T> skippedTransactions = new ArrayList<>();
        List<T> failedTransactions = new ArrayList<>();
        List<List<T>> batches = Lists.partition(items, BATCH_SIZE);
        for (List<T> batch: batches) {
            List<T> transactions = batch;
            int attempts = 0;
            while (attempts < MAX_RETRIES) {
                try {
                    TransactionResults<T> results = batchInsert.apply(conn, transactions);
                    if (!results.successfullTransactions().isEmpty()) {
                        conn.commit();
                    }
                    transactions = results.failedTransactions().RetryableTransactions();
                    successfulTransactions.addAll(results.successfullTransactions());
                    skippedTransactions.addAll(results.skippedTransactions());
                    failedTransactions.addAll(results.failedTransactions().UnRetryableTransactions());
                    if (results.failedTransactions().RetryableTransactions().isEmpty()) {
                        break;
                    }
                    attempts++;
                    if (attempts < MAX_RETRIES) {
                        Thread.sleep(1000 * (1L << attempts));
                    } else {
                        failedTransactions.addAll(transactions);
                    }
                } catch (SQLException E) {
                    try {
                        conn.rollback();
                    } catch (SQLException re) {
                        failedTransactions.addAll(transactions);
                        log.error("Rollback failed", re);
                    }
                    String state = E.getSQLState();
                    if (state != null && state.startsWith("08")) {
                        log.error("connection failed with SQL STATE {%s}".formatted(state));
                    }
                    attempts++;
                    log.error("batch attempt {%d}/{%d} failed with SQL STATE {%s}".formatted(attempts, MAX_RETRIES, state));
                    if (attempts < MAX_RETRIES) {
                        try {
                            Thread.sleep(1000 * (1L << attempts));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            log.warn("PsqlExecutor interrupted");
                            throw ie;
                        }
                    } else {
                        failedTransactions.addAll(transactions);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("PsqlExecutor interrupted");
                    throw ie;
                }
            }
        }
        if (!successfulTransactions.isEmpty()) {
            redisClient.set(keywords,nextTimeStamp);
        }
        return new TransactionResults<>(successfulTransactions, skippedTransactions, new FailedTransactions<>(List.of(), failedTransactions));
    }

}
