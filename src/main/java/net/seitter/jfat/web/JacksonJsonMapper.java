package net.seitter.jfat.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.json.JsonMapper;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.lang.reflect.Type;

/**
 * Jackson JSON mapper implementation for Javalin
 */
public class JacksonJsonMapper implements JsonMapper {
    
    private final ObjectMapper objectMapper;
    
    public JacksonJsonMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @NotNull
    @Override
    public String toJsonString(@NotNull Object obj, @NotNull Type type) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize object to JSON", e);
        }
    }
    
    @NotNull
    @Override
    public <T> T fromJsonString(@NotNull String json, @NotNull Type targetType) {
        try {
            return objectMapper.readValue(json, objectMapper.constructType(targetType));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize JSON to object", e);
        }
    }
    
    @NotNull
    @Override
    public <T> T fromJsonStream(@NotNull InputStream json, @NotNull Type targetType) {
        try {
            return objectMapper.readValue(json, objectMapper.constructType(targetType));
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize JSON stream to object", e);
        }
    }
} 