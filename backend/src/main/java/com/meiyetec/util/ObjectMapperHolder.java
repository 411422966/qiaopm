package com.meiyetec.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * 全局共享 ObjectMapper。
 * 复用 Spring 应用上下文里的同一个实例，避免在拦截器、过滤器、AOP 中各自 new。
 */
public final class ObjectMapperHolder {

    private ObjectMapperHolder() {}

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
}
