package com.mirror.ingestion;

import com.google.common.collect.ImmutableList;
import com.mirror.TestHelper;
import com.mirror.accessors.PsqlExecutor;
import com.mirror.models.v1.ImmutableRawAPINews;
import com.mirror.models.v1.PollStatus;
import com.mirror.queries.FailedTransactions;
import com.mirror.queries.TransactionResults;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
@Slf4j
public class PollingManagerTest {

    @Mock
    private ScheduledExecutorService executorServiceMock;
    @Mock
    private NewsPoller task1Mock;
    @Mock
    private NewsPoller task2Mock;
    @Mock
    private PsqlExecutor<ImmutableRawAPINews> psqlExecutorMock;
    @Mock
    private ScheduledFuture<?> scheduledFutureMock;

    private PollingManager pollingManager;
    private List<NewsPoller> tasks;

    private static final String KEYWORDS = "Gift,Lift,Rift";
    private static final String NEXT_TIMESTAMP = "1893893191";

    @BeforeEach
    public void setup() {
        tasks = List.of(task1Mock, task2Mock);
        pollingManager = new PollingManager(executorServiceMock, tasks, psqlExecutorMock);
    }

    // ─── startPolling ───────────────────────────────────────────────

    @Test
    public void testStartPollingSchedulesAllTasks() {
        Mockito.when(executorServiceMock.scheduleAtFixedRate(any(), anyLong(), eq(15L), eq(TimeUnit.MINUTES)))
                .thenReturn((ScheduledFuture) scheduledFutureMock);

        pollingManager.startPolling();

        Mockito.verify(executorServiceMock, Mockito.times(2))
                .scheduleAtFixedRate(any(), anyLong(), eq(15L), eq(TimeUnit.MINUTES));
    }


    // ─── runTask — success path ──────────────────────────────────────

    @Test
    public void testRunTaskSuccess() throws Exception {
        ImmutableList<ImmutableRawAPINews> articles = TestHelper.createNewsList(10);
        PollResults pollResults = Mockito.mock(PollResults.class);
        TransactionResults<ImmutableRawAPINews> transactionResults = Mockito.mock(TransactionResults.class);
        FailedTransactions<ImmutableRawAPINews> failedTransactions = Mockito.mock(FailedTransactions.class);

        Mockito.when(task1Mock.call()).thenReturn(pollResults);
        Mockito.when(task1Mock.getKeywords()).thenReturn(KEYWORDS);
        Mockito.when(pollResults.status()).thenReturn(PollStatus.SUCCESS);
        Mockito.when(pollResults.successfulNewsArticles()).thenReturn(articles);
        Mockito.when(pollResults.nextTimeStamp()).thenReturn(NEXT_TIMESTAMP);
        Mockito.when(psqlExecutorMock.applyBatchInsert(articles, KEYWORDS, NEXT_TIMESTAMP))
                .thenReturn(transactionResults);
        Mockito.when(transactionResults.failedTransactions()).thenReturn(failedTransactions);
        Mockito.when(failedTransactions.RetryableTransactions()).thenReturn(List.of());
        Mockito.when(failedTransactions.UnRetryableTransactions()).thenReturn(List.of());

        // trigger runTask via scheduleAtFixedRate capture
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.when(executorServiceMock.scheduleAtFixedRate(runnableCaptor.capture(), anyLong(), eq(15L), eq(TimeUnit.MINUTES)))
                .thenReturn((ScheduledFuture) scheduledFutureMock);

        pollingManager.startPolling();
        runnableCaptor.getAllValues().get(0).run(); // run task1Mock's runnable

        Mockito.verify(psqlExecutorMock, Mockito.times(1)).applyBatchInsert(articles, KEYWORDS, NEXT_TIMESTAMP);
    }

    @Test
    public void testRunTaskSuccessWithFailedTransactions() throws Exception {
        ImmutableList<ImmutableRawAPINews> articles = TestHelper.createNewsList(10);
        List<ImmutableRawAPINews> retryable = TestHelper.createNewsList(2);
        PollResults pollResults = Mockito.mock(PollResults.class);
        TransactionResults<ImmutableRawAPINews> transactionResults = Mockito.mock(TransactionResults.class);
        FailedTransactions<ImmutableRawAPINews> failedTransactions = Mockito.mock(FailedTransactions.class);

        Mockito.when(task1Mock.call()).thenReturn(pollResults);
        Mockito.when(task1Mock.getKeywords()).thenReturn(KEYWORDS);
        Mockito.when(pollResults.status()).thenReturn(PollStatus.SUCCESS);
        Mockito.when(pollResults.successfulNewsArticles()).thenReturn(articles);
        Mockito.when(pollResults.nextTimeStamp()).thenReturn(NEXT_TIMESTAMP);
        Mockito.when(psqlExecutorMock.applyBatchInsert(articles, KEYWORDS, NEXT_TIMESTAMP))
                .thenReturn(transactionResults);
        Mockito.when(transactionResults.failedTransactions()).thenReturn(failedTransactions);
        Mockito.when(failedTransactions.RetryableTransactions()).thenReturn(retryable);
        Mockito.when(failedTransactions.UnRetryableTransactions()).thenReturn(List.of());

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.when(executorServiceMock.scheduleAtFixedRate(runnableCaptor.capture(), anyLong(), eq(15L), eq(TimeUnit.MINUTES)))
                .thenReturn((ScheduledFuture) scheduledFutureMock);

        pollingManager.startPolling();
        runnableCaptor.getAllValues().get(0).run();

        Mockito.verify(psqlExecutorMock, Mockito.times(1)).applyBatchInsert(articles, KEYWORDS, NEXT_TIMESTAMP);
    }

    // ─── runTask — failure path ──────────────────────────────────────

    @Test
    public void testRunTaskPollFailed() throws Exception {
        PollResults pollResults = Mockito.mock(PollResults.class);

        Mockito.when(task1Mock.call()).thenReturn(pollResults);
        Mockito.when(task1Mock.getKeywords()).thenReturn(KEYWORDS);
        Mockito.when(pollResults.status()).thenReturn(PollStatus.FETCH_FAILURE);
        Mockito.when(pollResults.failedNewsArticles()).thenReturn("");

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.when(executorServiceMock.scheduleAtFixedRate(runnableCaptor.capture(), anyLong(), eq(15L), eq(TimeUnit.MINUTES)))
                .thenReturn((ScheduledFuture) scheduledFutureMock);

        pollingManager.startPolling();
        runnableCaptor.getAllValues().get(0).run();

        Mockito.verify(psqlExecutorMock, Mockito.never()).applyBatchInsert(any(), any(), any());
    }

    @Test
    public void testRunTaskExceptionDoesNotKillScheduler() throws Exception {
        Mockito.when(task1Mock.call()).thenThrow(new RuntimeException("unexpected"));
        Mockito.when(task1Mock.getKeywords()).thenReturn(KEYWORDS);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.when(executorServiceMock.scheduleAtFixedRate(runnableCaptor.capture(), anyLong(), eq(15L), eq(TimeUnit.MINUTES)))
                .thenReturn((ScheduledFuture) scheduledFutureMock);

        pollingManager.startPolling();

        // should not throw — exception is caught internally
        Assertions.assertDoesNotThrow(() -> runnableCaptor.getAllValues().get(0).run());
        Mockito.verify(psqlExecutorMock, Mockito.never()).applyBatchInsert(any(), any(), any());
    }

    // ─── stopPolling ─────────────────────────────────────────────────


    @Test
    public void testStopPollingShutdownClean() throws Exception {
        Mockito.when(executorServiceMock.awaitTermination(30, TimeUnit.SECONDS)).thenReturn(true);

        pollingManager.stopPolling();

        Mockito.verify(executorServiceMock, Mockito.times(1)).shutdown();
        Mockito.verify(executorServiceMock, Mockito.times(1)).awaitTermination(30, TimeUnit.SECONDS);
        Mockito.verify(executorServiceMock, Mockito.never()).shutdownNow();
    }

    @Test
    public void testStopPollingShutdownForced() throws Exception {
        Mockito.when(executorServiceMock.awaitTermination(30, TimeUnit.SECONDS)).thenReturn(false);

        pollingManager.stopPolling();

        Mockito.verify(executorServiceMock, Mockito.times(1)).shutdown();
        Mockito.verify(executorServiceMock, Mockito.times(1)).shutdownNow();
    }

    @Test
    public void testStopPollingInterrupted() throws Exception {
        Mockito.when(executorServiceMock.awaitTermination(30, TimeUnit.SECONDS))
                .thenThrow(new InterruptedException());

        pollingManager.stopPolling();

        Mockito.verify(executorServiceMock, Mockito.times(1)).shutdownNow();
        Assertions.assertTrue(Thread.interrupted()); // verify flag was restored
    }

}