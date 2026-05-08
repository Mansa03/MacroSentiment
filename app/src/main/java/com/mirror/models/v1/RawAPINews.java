package com.mirror.models.v1;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@Value.Style(jdkOnly = true)
@JsonDeserialize(as = ImmutableRawAPINews.class)
public interface RawAPINews {
    Source source();

    @Nullable
    String author();

    String title();

    @Nullable
    String description();

    String url();

    @Nullable
    String urlToImage();

    String publishedAt();

    String content();
}
