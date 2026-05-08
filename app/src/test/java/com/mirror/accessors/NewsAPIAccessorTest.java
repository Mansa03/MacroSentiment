package com.mirror.accessors;


import java.io.IOException;
import java.util.Optional;
import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest;
import java.net.URLEncoder;
import java.util.List;

import com.mirror.models.v1.NewsAPIStatusCodes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assertions;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mirror.ingestion.ResponseResult;

@ExtendWith(MockitoExtension.class)
public class NewsAPIAccessorTest {
    private static final String DUMMY_KEY = "dummy_key";
    @Mock
    private HttpClient httpClientMock;
    private NewsAPIAccessor newsAPIAccessor;

    private class DummyHttpResponse<T> implements HttpResponse<T> {
        private int statusCode;
        private T body;
        private HttpHeaders headers;
        private HttpRequest request;
        private Optional<SSLSession> sslSession;
        private URI uri;
        private HttpClient.Version version;
        private Optional<HttpResponse<T>> previousResponse;

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public T body() {
            return body;
        }

        @Override
        public HttpHeaders headers() {
            return headers;
        }

        @Override
        public HttpRequest request() {
            return request;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return sslSession;  
        }

        @Override
        public URI uri() {
            return uri;
        }

        @Override
        public HttpClient.Version version() {
            return version;
        }

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return previousResponse;
        }

        public DummyHttpResponse(int statusCode, HttpRequest request, T body, HttpHeaders headers, Optional<SSLSession> sslSession, URI uri, HttpClient.Version version) {
            this.statusCode = statusCode;
            this.body = body;
            this.headers = headers;
            this.request = request;
            this.sslSession = sslSession;
            this.uri = uri;
            this.version = version;
            this.previousResponse = Optional.empty();
        }

    }

    private final String OK_STATUS = "OK";
    private final String ERROR_STATUS = "ERROR";
    private final String TOTAL_RESULTS_1 = "1";
    private final String PUBLISHED_FROM_1 = "2024-06-01T12:00:00Z";
    private final String AUTHOR_1 = "John Doe";
    private final String TITLE_1 = "Example News Title";
    private final String DESCRIPTION_1 = "This is an example news description.";
    private final String URL_1 = "https://example.com/news/1";
    private final String URL_TO_IMAGE_1 = "https://example.com/image.jpg";
    private final String CONTENT_1 = "This is the content of the example news article.";
    private final String NAME_1 = "TEST 1";
    private final String ID_1 = "test1";

    private final List<String> QUERY1_LIST = List.of("test");
    private final List<String> QUERY_MULTIPLE_LIST = List.of("test", "example");
    private final String QUERY1 = "test";
    private final String QUERY_MULTIPLE = "test OR example";
    private final String DOMAINS = "reuters.com,bloomberg.com,wsj.com,ft.com,cnbc.com";

    private final String EXPECTED_URI = String.format(
            "https://newsapi.org/v2/everything?q=%s&domains=%s&language=en&from=%s&apiKey=%s", QUERY1, DOMAINS, PUBLISHED_FROM_1,
            DUMMY_KEY);
    
    private final String EXPECTED_MULTIPLE_URI = String.format(
            "https://newsapi.org/v2/everything?q=%s&domains=%s&language=en&from=%s&apiKey=%s", URLEncoder.encode(QUERY_MULTIPLE, java.nio.charset.StandardCharsets.UTF_8), DOMAINS, PUBLISHED_FROM_1,
            DUMMY_KEY);

        private final String DUMMY_RESPONSE = String.format(
            "{\"status\":\"%s\",\"totalResults\":%s,\"articles\":[{\"source\":{\"id\":\"%s\",\"name\":\"%s\"},\"author\":\"%s\",\"title\":\"%s\",\"description\":\"%s\",\"url\":\"%s\",\"urlToImage\":\"%s\",\"publishedAt\":\"%s\",\"content\":\"%s\"}]}",
            OK_STATUS, TOTAL_RESULTS_1, ID_1, NAME_1, AUTHOR_1, TITLE_1, DESCRIPTION_1, URL_1, URL_TO_IMAGE_1,
            PUBLISHED_FROM_1, CONTENT_1);
        private final String DUMMY_ERROR_RESPONSE = String.format(
            "{\"status\":\"%s\",\"code\":\"%s\",\"message\":\"%s\"}",
            ERROR_STATUS, "apiKeyInvalid", "Your API key is invalid or incorrect.");
        private final String DUMMY_RESPONSE_MULTIPLE_KEYWORDS = String.format(
            "{\"status\":\"%s\",\"totalResults\":%s,\"articles\":[{\"source\":{\"id\":\"%s\",\"name\":\"%s\"},\"author\":\"%s\",\"title\":\"%s\",\"description\":\"%s\",\"url\":\"%s\",\"urlToImage\":\"%s\",\"publishedAt\":\"%s\",\"content\":\"%s\"}]}",
            OK_STATUS, TOTAL_RESULTS_1, ID_1, NAME_1, AUTHOR_1, TITLE_1, DESCRIPTION_1, URL_1, URL_TO_IMAGE_1,
            PUBLISHED_FROM_1, CONTENT_1);
        private final HttpResponse<Object> DUMMY_HTTP_RESPONSE = new DummyHttpResponse<>(200, null, DUMMY_RESPONSE, null, Optional.empty(), URI.create(EXPECTED_URI), null);
        private final HttpResponse<Object> DUMMY_HTTP_THROTTLING_RESPONSE = new DummyHttpResponse<>(429, null, DUMMY_RESPONSE, null, Optional.empty(), URI.create(EXPECTED_URI), null);
        private final HttpResponse<Object> DUMMY_HTTP_RESPONSE_MULTIPLE_KEYWORDS = new DummyHttpResponse<>(200, null, DUMMY_RESPONSE_MULTIPLE_KEYWORDS, null, Optional.empty(), URI.create(EXPECTED_MULTIPLE_URI), null);
        private final HttpResponse<Object> DUMMY_HTTP_RESPONSE_UNAUTHORIZED = new DummyHttpResponse<>(401, null, DUMMY_RESPONSE, null, Optional.empty(), URI.create(EXPECTED_URI), null);
    private final HttpResponse<Object> DUMMY_HTTP_RESPONSE_BAD_REQUEST = new DummyHttpResponse<>(400, null, DUMMY_RESPONSE, null, Optional.empty(), URI.create(EXPECTED_URI), null);
    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        newsAPIAccessor = new NewsAPIAccessor(DUMMY_KEY, httpClientMock);
    }

    @Test
    public void testGetNews() {
        try{
        Mockito.when(httpClientMock.send(Mockito.any(), Mockito.any())).thenReturn(DUMMY_HTTP_RESPONSE);
        ResponseResult response = newsAPIAccessor.getNews(QUERY1_LIST, PUBLISHED_FROM_1);
        Mockito.verify(httpClientMock, Mockito.times(1)).send(Mockito.any(), Mockito.any());
        Assertions.assertEquals(EXPECTED_URI, response.uri().toString());
        Assertions.assertEquals(response.responseBody().orElse(null), DUMMY_HTTP_RESPONSE.body());
            Assertions.assertSame(NewsAPIStatusCodes.OK, response.statusCode());
        } catch (Exception e) {
            Assertions.fail("Exception thrown: " + e.getMessage());
        }
    }

    @Test
    public void testGetNewsWithMultipleKeywords() {
        try {
            Mockito.when(httpClientMock.send(Mockito.any(), Mockito.any())).thenReturn(DUMMY_HTTP_RESPONSE_MULTIPLE_KEYWORDS);
            ResponseResult response = newsAPIAccessor.getNews(QUERY_MULTIPLE_LIST, PUBLISHED_FROM_1);
            Assertions.assertEquals(EXPECTED_MULTIPLE_URI, response.uri().toString());
            Assertions.assertEquals(response.responseBody().orElse(null), DUMMY_HTTP_RESPONSE_MULTIPLE_KEYWORDS.body());
            Assertions.assertSame(NewsAPIStatusCodes.OK, response.statusCode());
        } catch (Exception e) {
            Assertions.fail("Exception thrown: " + e.getMessage());
        }
    }

    @Test
    public void testGetNewsWithRetries() {
        try {
            Mockito.when(httpClientMock.send(Mockito.any(), Mockito.any()))
                    .thenThrow(new IOException("Network error"))
                    .thenThrow(new IOException("Network error"))
                    .thenReturn(DUMMY_HTTP_RESPONSE);
           ResponseResult response = newsAPIAccessor.getNews(QUERY1_LIST, PUBLISHED_FROM_1);
            Mockito.verify(httpClientMock, Mockito.times(3)).send(Mockito.any(), Mockito.any());
            Assertions.assertEquals(response.responseBody().orElse(null), DUMMY_HTTP_RESPONSE.body());
            Assertions.assertSame(NewsAPIStatusCodes.OK, response.statusCode());
        } catch (Exception e) {
            Assertions.fail("Exception thrown: " + e.getMessage());
        }
    }

    @Test
    public void testGetNewsWithRetriesExhausted() {
        try {
            Mockito.when(httpClientMock.send(Mockito.any(), Mockito.any()))
                    .thenThrow(new IOException("Network error"));
            ResponseResult response = newsAPIAccessor.getNews(QUERY1_LIST, PUBLISHED_FROM_1);
            Mockito.verify(httpClientMock, Mockito.times(3)).send(Mockito.any(), Mockito.any());
            Assertions.assertTrue(response.responseBody().isEmpty());
            Assertions.assertSame(NewsAPIStatusCodes.SERVER_ERROR, response.statusCode());
        } catch (Exception e) {
            Assertions.fail("Exception thrown: " + e.getMessage());
        }
    }

    @Test
    public void testGetNewsWithThrottlingExhausted() {
        try {
            Mockito.when(httpClientMock.send(Mockito.any(), Mockito.any()))
                    .thenReturn(DUMMY_HTTP_THROTTLING_RESPONSE);
            ResponseResult response = newsAPIAccessor.getNews(QUERY1_LIST, PUBLISHED_FROM_1);
            Mockito.verify(httpClientMock, Mockito.times(3)).send(Mockito.any(), Mockito.any());
            Assertions.assertTrue(response.responseBody().isEmpty());
            Assertions.assertSame(NewsAPIStatusCodes.THROTTLING, response.statusCode());
        } catch (Exception e) {
            Assertions.fail("Exception thrown: " + e.getMessage());
        }
    }

    @Test
    public void testGetNewsUnauthorized() {
        try {
            Mockito.when(httpClientMock.send(Mockito.any(), Mockito.any()))
                    .thenReturn(DUMMY_HTTP_RESPONSE_UNAUTHORIZED);
            ResponseResult response = newsAPIAccessor.getNews(QUERY1_LIST, PUBLISHED_FROM_1);
            Mockito.verify(httpClientMock, Mockito.times(1)).send(Mockito.any(), Mockito.any());
            Assertions.assertTrue(response.responseBody().isEmpty());
            Assertions.assertSame(NewsAPIStatusCodes.UNAUTHORIZED, response.statusCode());
        } catch (Exception e) {
            Assertions.fail("Exception thrown: " + e.getMessage());
        }
    }

    @Test
    public void testGetNewsBadRequset(){
        try {
            Mockito.when(httpClientMock.send(Mockito.any(), Mockito.any()))
                    .thenReturn(DUMMY_HTTP_RESPONSE_BAD_REQUEST);
            ResponseResult response = newsAPIAccessor.getNews(QUERY1_LIST, PUBLISHED_FROM_1);
            Mockito.verify(httpClientMock, Mockito.times(1)).send(Mockito.any(), Mockito.any());
            Assertions.assertTrue(response.responseBody().isEmpty());
            Assertions.assertSame(NewsAPIStatusCodes.BAD_REQUEST, response.statusCode());
        } catch (Exception e) {
            Assertions.fail("Exception thrown: " + e.getMessage());
        }
    }



    @Test
    public void testBadParameters() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> newsAPIAccessor.getNews(List.of(), PUBLISHED_FROM_1));
        Assertions.assertThrows(NullPointerException.class, () -> newsAPIAccessor.getNews(QUERY1_LIST, null));
    }

}
