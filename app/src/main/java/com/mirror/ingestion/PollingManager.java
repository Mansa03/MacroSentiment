package com.mirror.ingestion;

import com.mirror.accessors.PsqlExecutor;
import com.mirror.models.v1.ImmutableRawAPINews;
import com.mirror.models.v1.PollStatus;
import com.mirror.queries.FailedTransactions;
import com.mirror.queries.TransactionResults;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@AllArgsConstructor
@Slf4j
public class PollingManager {
    @NonNull
    private final ScheduledExecutorService executorService;
    @NonNull
    private final List<NewsPoller> tasks;
    @NonNull
    private final PsqlExecutor<ImmutableRawAPINews> psqlExecutor;

    private final Map<NewsPoller, ScheduledFuture<?>> taskHandles = new ConcurrentHashMap<>();

    public void startPolling() {
        // Schedule the newsPollers to run at fixed intervals (e.g., every 15 mins)
        tasks.forEach(task -> {
            long jitter = ThreadLocalRandom.current().nextLong(0, 5); // 0–4 mins, always non-negative

            ScheduledFuture<?> handle = executorService.scheduleAtFixedRate(
                    () -> runTask(task),
                    jitter,
                    15,
                    TimeUnit.MINUTES
            );
            taskHandles.put(task, handle);
        });
    }

    private void runTask(NewsPoller task) {
        try {
            PollResults results = task.call();

            if (results.status() == PollStatus.SUCCESS) {
                TransactionResults<ImmutableRawAPINews> transactionResults =
                        psqlExecutor.applyBatchInsert(
                                results.successfulNewsArticles(),
                                task.getKeywords(),
                                results.nextTimeStamp()
                        );

                log.info("Stored {} articles for keywords {}",
                        results.successfulNewsArticles().size(), task.getKeywords());

                FailedTransactions<ImmutableRawAPINews> failed = transactionResults.failedTransactions();
                if (!failed.RetryableTransactions().isEmpty() || !failed.UnRetryableTransactions().isEmpty()) {
                    log.warn("Failed to store {} transactions for keywords {} — queuing for retry",
                            failed.RetryableTransactions().size() + failed.UnRetryableTransactions().size(), task.getKeywords());
                    //handle failures
                }

            } else {
                log.warn("Poll failed for keywords {}: {}", task.getKeywords(), results.failedNewsArticles());
            }

        } catch (Exception e) {
            log.error("Unhandled exception in poller for keywords {} — task will continue", task.getKeywords(), e);
        }
    }

    public void stopPolling() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

}