package com.mirror.models.v1;


import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(jdkOnly = true)
@JsonDeserialize(as = ImmutableAPINewsResponse.class)
public interface APINewsResponse {
    String status();
    int totalResults();
    List<ImmutableRawAPINews> articles();
    
}