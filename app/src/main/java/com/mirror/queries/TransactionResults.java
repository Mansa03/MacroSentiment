package com.mirror.queries;

import java.util.List;

public record TransactionResults<T>(List<T> successfullTransactions, List<T> skippedTransactions, FailedTransactions<T> failedTransactions) {}
