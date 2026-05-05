package com.mirror.ingestion;
import com.mirror.models.v1.ImmutableRawAPINews;
import com.google.common.collect.ImmutableList;
import com.mirror.models.v1.PollStatus;

public record PollResults(ImmutableList<ImmutableRawAPINews> successfulNewsArticles, String failedNewsArticles, String nextTimeStamp, PollStatus status) { }
