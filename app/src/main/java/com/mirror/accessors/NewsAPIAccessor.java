package com.mirror.accessors;

import com.google.common.base.Preconditions;
import com.mirror.ingestion.ResponseResult;
import com.mirror.models.v1.NewsAPIStatusCodes;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

@AllArgsConstructor
@Slf4j
public class NewsAPIAccessor {

    private static final int MAX_RETRIES = 3;
    private static final String BASE_URL =
            "https://newsapi.org/v2/everything?q=";
    private static final List<String> DOMAINS = List.of(
            "reuters.com",
            "bloomberg.com",
            "wsj.com",
            "ft.com",
            "cnbc.com",
            "finance.yahoo.com",
            "marketwatch.com",
            "economist.com",
            "apnews.com",
            "bbc.com"
    );

    @NonNull
    private String apiKey;

    @NonNull
    private HttpClient httpClient;

    public ResponseResult getNews(
            @NonNull List<String> keywords,
            @NonNull String fromTimeStamp
    ) throws InterruptedException {
        Preconditions.checkArgument(
                !keywords.isEmpty(),
                "Keywords list cannot be empty"
        );
        URI uri = URI.create(buildURI(keywords, fromTimeStamp));
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        for (int attempts = 0; attempts < MAX_RETRIES; attempts++) {
            try {
                log.info("Attempting to fetch news with URI: {}", uri);
                HttpResponse<String> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );
                NewsAPIStatusCodes status = NewsAPIStatusCodes.fromInt(response.statusCode()).orElse(NewsAPIStatusCodes.SERVER_ERROR);
                if (status == NewsAPIStatusCodes.OK) {
                    return new ResponseResult(uri, Optional.of(response.body()), status);
                } else {
                    if (status == NewsAPIStatusCodes.THROTTLING) {
                        String message = "NewsAPI Throttling with ErrorCode %d".formatted(response.statusCode());
                        if (handleRetry(attempts, uri, keywords, fromTimeStamp, message)) {
                            continue;
                        }
                        return new ResponseResult(uri, Optional.empty(), status);
                    } else {
                        return new ResponseResult(uri, Optional.empty(), status);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Thread interrupted while fetching news");
                throw e;
            } catch (IOException e) {
                if (handleRetry(attempts, uri, keywords, fromTimeStamp, e.getMessage())) {
                    continue;
                }
            }
        }
        return new ResponseResult(uri, Optional.empty(), NewsAPIStatusCodes.SERVER_ERROR);
    }

    private String buildURI(
            @NonNull List<String> keywords,
            @NonNull String fromTimeStamp
    ) {
        StringBuilder uriBuilder = new StringBuilder(BASE_URL);
        String query = String.join(" OR ", keywords);
        query = URLEncoder.encode(
                query,
                java.nio.charset.StandardCharsets.UTF_8
        );
        String domains = String.join(",", DOMAINS);
        uriBuilder
                .append(query)
                .append("&domains=")
                .append(domains)
                .append("&language=en")
                .append("&from=")
                .append(fromTimeStamp)
                .append("&apiKey=")
                .append(apiKey);
        return uriBuilder.toString();
    }

    private void backoff(int attempts) throws InterruptedException {
        // backoff
        long delay = 1000L * (1L << attempts);
        Thread.sleep(delay);

    }

    private boolean handleRetry(int attempts, URI uri, List<String> keywords, String fromTimeStamp, String message) throws InterruptedException {
        log.error(
                "Attempt {}/{} failed for URI {}: {}",
                attempts + 1,
                MAX_RETRIES,
                uri,
                message
        );
        if (attempts == MAX_RETRIES - 1) {
            log.error(
                    "Max retries reached. Failed for keywords: {}, timestamp: {}",
                    keywords,
                    fromTimeStamp
            );
            return false;
        }
        backoff(attempts + 1);
        return true;
    }

}
