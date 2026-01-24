package com.ako.dbuff.config;

import com.ako.dbuff.dotapi.api.ConstantsApi;
import com.ako.dbuff.dotapi.api.MatchesApi;
import com.ako.dbuff.dotapi.api.PlayersApi;
import com.ako.dbuff.dotapi.api.PublicMatchesApi;
import com.ako.dbuff.dotapi.invoker.ApiClient;
import com.ako.dbuff.dotapi.invoker.JSON;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.util.concurrent.RateLimiter;
import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.client.ClientRequestFilter;
import java.net.URI;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Slf4j
@Configuration
public class DotaApiConfig {

  private final DotaApiConfigurationProperties dotaApiConfigurationProperties;

  @Autowired
  public DotaApiConfig(DotaApiConfigurationProperties dotaApiConfigurationProperties) {
    this.dotaApiConfigurationProperties = dotaApiConfigurationProperties;
    log.info(
        "Enabled api_key for dota_api: {}",
        StringUtils.hasLength(dotaApiConfigurationProperties.getApiKey()));
  }

  /**
   * Configure the JSON ObjectMapper for the DotaAPI client. This enables ALLOW_COERCION_OF_SCALARS
   * which is required for proper deserialization of the API responses (e.g.,
   * GetConstantsByResource200Response).
   */
  @PostConstruct
  public void configureJsonMapper() {
    ObjectMapper mapper =
        JsonMapper.builder()
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .enable(MapperFeature.ALLOW_COERCION_OF_SCALARS) // Enable coercion of scalars
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
            .enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING)
            .addModule(new JavaTimeModule())
            .addModule(new JsonNullableModule())
            .build();

    // Create a custom JSON instance with the configured mapper
    JSON customJson =
        new JSON() {
          @Override
          public ObjectMapper getContext(Class<?> type) {
            return mapper;
          }

          @Override
          public ObjectMapper getMapper() {
            return mapper;
          }
        };

    // Set as the default JSON instance for the API client
    JSON.setDefault(customJson);
    log.info("Configured DotaAPI JSON mapper with ALLOW_COERCION_OF_SCALARS enabled");
  }

  @Bean
  public RateLimiter dotaApiRateLimiter() {
    double permitsPerSec = dotaApiConfigurationProperties.getRequestPerMinute() / 60d;
    log.info("dota api permitsPerSec: {}", permitsPerSec);
    return RateLimiter.create(permitsPerSec);
  }

  @Bean
  public ApiClient getApiClient() {
    ApiClient apiClient = new ApiClient();
    apiClient.setDebugging(dotaApiConfigurationProperties.getIsDebugEnabled());

    // Only add API key filter if API key is provided
    if (StringUtils.hasLength(dotaApiConfigurationProperties.getApiKey())) {
      apiClient
          .getHttpClient()
          .register(
              (ClientRequestFilter)
                  requestContext -> {
                    URI uri = requestContext.getUri();

                    requestContext.setUri(
                        buildUriWithApiKey(uri, dotaApiConfigurationProperties.getApiKey()));
                  });
    }

    return apiClient;
  }

  private URI buildUriWithApiKey(URI uri, String apiKey) {
    return URI.create(uri.toString() + "?api_key=" + apiKey);
  }

  @Data
  @Configuration
  @ConfigurationProperties(prefix = "dota-api")
  public static class DotaApiConfigurationProperties {
    private String apiKey;
    private Integer requestPerMinute = 60;
    private Boolean isDebugEnabled = false;
  }

  @Bean
  public MatchesApi matchesApi(@Autowired ApiClient apiClient) {
    return new MatchesApi(apiClient);
  }

  @Bean
  public ConstantsApi constantsApi(@Autowired ApiClient apiClient) {
    return new ConstantsApi(apiClient);
  }

  @Bean
  public PlayersApi playersApi(@Autowired ApiClient apiClient) {
    return new PlayersApi(apiClient);
  }

  @Bean
  public PublicMatchesApi publicMatchesApi(@Autowired ApiClient apiClient) {
    return new PublicMatchesApi(apiClient);
  }
}
