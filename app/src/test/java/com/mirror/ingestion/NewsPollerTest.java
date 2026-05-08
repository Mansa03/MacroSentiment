package com.mirror.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirror.TestHelper;
import com.mirror.accessors.NewsAPIAccessor;
import com.mirror.accessors.RedisAccessor;
import com.mirror.models.v1.NewsAPIStatusCodes;
import com.mirror.models.v1.PollStatus;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;


@ExtendWith(MockitoExtension.class)
@Slf4j
public class NewsPollerTest {
    @Mock
    static RedisAccessor redisAccessorMock;
    @Mock
    private  NewsAPIAccessor apiAccessorMock;
    @Captor
    private ArgumentCaptor<String> timeStampCaptor;
    @Captor
    private ArgumentCaptor<String> keywordsCaptor;
    private  final List<String> KEYWORDS = List.of("Gift","Lift","Rift","Sift","Sith");
    private  ObjectMapper mapper;
    private  NewsPoller poller;
    private final URI TEST_URI = URI.create("DUMMY_URI");
    private static final String FROM_TIMESTAMP = "1893893191";
    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        mapper = new ObjectMapper();
        //SimpleModule module = new SimpleModule();
        //module.addDeserializer(ImmutableRawAPINews.class, new CustomRawAPINewsDeserializer());
        //mapper.registerModule(module);
        poller = new NewsPoller(apiAccessorMock,KEYWORDS,redisAccessorMock,mapper);
    }

    @Test
    public void testOKResponse() {
        try {
            ResponseResult responseResult = TestHelper.createResponseResult(100, NewsAPIStatusCodes.OK, TEST_URI);
            Mockito.when(redisAccessorMock.get(any())).thenReturn(Optional.of(FROM_TIMESTAMP));
            Mockito.when(apiAccessorMock.getNews(KEYWORDS,FROM_TIMESTAMP)).thenReturn(responseResult);
            PollResults results = poller.call();
            Mockito.verify(redisAccessorMock,Mockito.times(1)).get(any());
            Mockito.verify(apiAccessorMock, Mockito.times(1)).getNews(KEYWORDS,FROM_TIMESTAMP);
            Assertions.assertNull(results.failedNewsArticles());
            Assertions.assertSame(PollStatus.SUCCESS, results.status());
            Assertions.assertEquals(100,results.successfulNewsArticles().size(), "expected %s got %s".formatted(100,results.successfulNewsArticles().size()));
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    @Test
    public void testDeserializationFailure() {
        try {
            ResponseResult responseResult = new ResponseResult(TEST_URI,Optional.of("purposefully fail"),NewsAPIStatusCodes.OK);
            Mockito.when(redisAccessorMock.get(any())).thenReturn(Optional.of(FROM_TIMESTAMP));
            Mockito.when(apiAccessorMock.getNews(KEYWORDS,FROM_TIMESTAMP)).thenReturn(responseResult);
            PollResults results = poller.call();
            Mockito.verify(redisAccessorMock,Mockito.times(1)).get(any());
            Mockito.verify(apiAccessorMock, Mockito.times(1)).getNews(KEYWORDS,FROM_TIMESTAMP);
            Assertions.assertNotNull(results.failedNewsArticles());
            Assertions.assertTrue(results.successfulNewsArticles().isEmpty());
            Assertions.assertSame(PollStatus.PARSE_FAILURE, results.status());
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    @Test
    public void testFetchFailureThrottling(){
        try {
            ResponseResult responseResult = TestHelper.createResponseResult(0, NewsAPIStatusCodes.THROTTLING, TEST_URI);
            Mockito.when(redisAccessorMock.get(any())).thenReturn(Optional.of(FROM_TIMESTAMP));
            Mockito.when(apiAccessorMock.getNews(KEYWORDS,FROM_TIMESTAMP)).thenReturn(responseResult);
            PollResults results = poller.call();
            Mockito.verify(redisAccessorMock,Mockito.times(1)).get(any());
            Mockito.verify(apiAccessorMock, Mockito.times(1)).getNews(KEYWORDS,FROM_TIMESTAMP);
            Assertions.assertNotNull(results.failedNewsArticles());
            Assertions.assertTrue(results.successfulNewsArticles().isEmpty());
            Assertions.assertSame(PollStatus.FETCH_FAILURE, results.status());
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    @Test
    public void testSuccessWithNoTimestampInRedis() {
        try {
            ResponseResult responseResult = TestHelper.createResponseResult(100, NewsAPIStatusCodes.OK, TEST_URI);
            Mockito.when(redisAccessorMock.get(any())).thenReturn(Optional.empty());
            Mockito.when(apiAccessorMock.getNews(eq(KEYWORDS),any())).thenReturn(responseResult);
            PollResults results = poller.call();
            Mockito.verify(apiAccessorMock, Mockito.times(1)).getNews(eq(KEYWORDS),timeStampCaptor.capture());
            Mockito.verify(redisAccessorMock,Mockito.times(1)).get(any());
            Assertions.assertSame(PollStatus.SUCCESS, results.status());
            Assertions.assertNull(results.failedNewsArticles());
            Assertions.assertEquals(100, results.successfulNewsArticles().size());
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    @Test
    public void testInterruptHandling() {
        try {
            Mockito.when(redisAccessorMock.get(any())).thenReturn(Optional.of(FROM_TIMESTAMP));
            Mockito.when(apiAccessorMock.getNews(eq(KEYWORDS),any())).thenThrow(new InterruptedException("testing interrupt handling"));
            PollResults results = poller.call();
            assert false;
        } catch (InterruptedException e) {
            assert true;
        }
    }


}
