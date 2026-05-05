package com.mirror.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.mirror.accessors.NewsAPIAccessor;
import com.mirror.accessors.RedisAccessor;
import com.mirror.deserializers.v1.CustomRawAPINewsDeserializer;
import com.mirror.models.v1.ImmutableRawAPINews;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.util.List;


@ExtendWith(MockitoExtension.class)
@Slf4j
public class NewsPollerTest {
    @Mock
    static RedisAccessor redisAccessor;
    @Mock
    private static NewsAPIAccessor apiAccessor;
    private static final List<String> KEYWORDS = List.of("Gift","Lift","Rift","Sift","Sith");
    private static  ObjectMapper mapper;
    private static NewsPoller poller;

    @BeforeAll
    public static void setup() {
        mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(ImmutableRawAPINews.class, new CustomRawAPINewsDeserializer());
        mapper.registerModule(module);
        poller = new NewsPoller(apiAccessor,KEYWORDS,redisAccessor,mapper);
    }


}
