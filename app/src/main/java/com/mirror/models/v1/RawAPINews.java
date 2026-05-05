package com.mirror.models.v1;
import org.immutables.value.Value;

import java.util.UUID;

@Value.Immutable
public interface RawAPINews {
    String sourceId();
    String sourceName();
    String author();
    String title();
    String description();
    String url();
    String urlToImage();
    String publishedAt();
    String content();
}
