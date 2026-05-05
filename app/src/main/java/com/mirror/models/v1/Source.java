package com.mirror.models.v1;

import org.immutables.value.Value;

@Value.Immutable
@Value.Style(jdkOnly = true)
public interface Source {
    String id();
    String name();

}
