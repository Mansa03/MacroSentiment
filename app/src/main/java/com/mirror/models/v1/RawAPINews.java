package com.mirror.models.v1;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

import java.util.UUID;

@Value.Immutable
@Value.Style(jdkOnly = true)
@JsonDeserialize(as=ImmutableRawAPINews.class)
public interface RawAPINews {
    Source source();
    String author();
    String title();
    String description();
    String url();
    String urlToImage();
    String publishedAt();
    String content();
}
