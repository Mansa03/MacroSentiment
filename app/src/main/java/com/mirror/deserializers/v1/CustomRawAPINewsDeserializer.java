package com.mirror.deserializers.v1;

import com.mirror.models.v1.ImmutableRawAPINews;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;




public class CustomRawAPINewsDeserializer extends StdDeserializer<ImmutableRawAPINews> {
    public CustomRawAPINewsDeserializer() {
        this(null);
    }

    public CustomRawAPINewsDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public ImmutableRawAPINews deserialize( JsonParser jp, DeserializationContext ctxt) throws java.io.IOException {
        JsonNode node = jp.getCodec().readTree(jp);
        JsonNode sourceNode = node.get("source");
        String sourceId = sourceNode.get("id").asText();
        String sourceName = sourceNode.get("name").asText();
        String author = node.get("author").asText();
        String title = node.get("title").asText();
        String description = node.get("description").asText();
        String url = node.get("url").asText();
        String urlToImage = node.get("urlToImage").asText();
        String publishedAt = node.get("publishedAt").asText();
        String content = node.get("content").asText();

        return ImmutableRawAPINews.builder()
                .sourceId(sourceId)
                .sourceName(sourceName)
                .author(author)
                .title(title)
                .description(description)
                .url(url)
                .urlToImage(urlToImage)
                .publishedAt(publishedAt)
                .content(content)
                .build();
    }
    
}
