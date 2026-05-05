package com.mirror.models.v1;


import java.util.List;
import org.immutables.value.Value;

@Value.Immutable
public interface APINewsResponse {
    String status();
    int totalResults();
    List<ImmutableRawAPINews> articles();
    
}