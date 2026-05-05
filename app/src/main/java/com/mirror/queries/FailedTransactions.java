package com.mirror.queries;

import java.util.List;
public record FailedTransactions<T>(List<T> RetryableTransactions, List<T> UnRetryableTransactions) {
}
