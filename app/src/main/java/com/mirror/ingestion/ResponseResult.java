package com.mirror.ingestion;

import java.net.URI;
import java.util.Optional;

import com.mirror.models.v1.NewsAPIStatusCodes;

import lombok.NonNull;

public record ResponseResult( @NonNull URI uri, @NonNull Optional<String> responseBody, NewsAPIStatusCodes statusCode) { }
