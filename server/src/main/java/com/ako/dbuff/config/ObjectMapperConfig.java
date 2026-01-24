package com.ako.dbuff.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class ObjectMapperConfig {

  /**
   * Creates the default ObjectMapper with common configurations.
   * 
   * - ALLOW_COERCION_OF_SCALARS - enables coercion of scalar values (e.g., String to Number)
   *   which is required by the generated OpenAPI client (GetConstantsByResource200Response).
   * - JavaTimeModule - enables support for Java 8 date/time types (LocalDateTime, etc.)
   * - WRITE_DATES_AS_TIMESTAMPS disabled - writes dates as ISO-8601 strings instead of arrays
   */
  @Bean
  @Primary
  public ObjectMapper objectMapper() {
    return JsonMapper.builder()
        .enable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
        .serializationInclusion(JsonInclude.Include.NON_NULL)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .addModule(new JavaTimeModule())
        .build();
  }

  /**
   * Static factory method for creating ObjectMapper instances outside of Spring context.
   * @deprecated Use the Spring-managed bean instead when possible.
   */
  @Deprecated
  public static ObjectMapper defaultObjectMapper() {
    return JsonMapper.builder()
        .enable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
        .serializationInclusion(JsonInclude.Include.NON_NULL)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .addModule(new JavaTimeModule())
        .build();
  }
}
