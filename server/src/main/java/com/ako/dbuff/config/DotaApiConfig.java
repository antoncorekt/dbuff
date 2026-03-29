package com.ako.dbuff.config;

import com.ako.dbuff.dotapi.api.ConstantsApi;
import com.ako.dbuff.dotapi.api.MatchesApi;
import com.ako.dbuff.dotapi.api.PlayersApi;
import com.ako.dbuff.dotapi.api.PublicMatchesApi;
import com.ako.dbuff.dotapi.invoker.ApiClient;
import com.google.common.util.concurrent.RateLimiter;
import jakarta.ws.rs.client.ClientRequestFilter;
import java.net.URI;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
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
