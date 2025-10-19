package com.ako.dbuff.config;

import com.google.common.util.concurrent.RateLimiter;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Slf4j
@Configuration
public class ScrapperConfig {

  private final ScrapperConfigurationProperties scrapperConfigurationProperties;

  public ScrapperConfig(ScrapperConfigurationProperties scrapperConfigurationProperties) {
    this.scrapperConfigurationProperties = scrapperConfigurationProperties;
    log.info(
        "Use api_key for scrapper service: {}",
        StringUtils.hasLength(scrapperConfigurationProperties.getApiKey()));
    log.debug("Scrapper API key value: '{}'", scrapperConfigurationProperties.getApiKey());
    log.debug("Scrapper URL: '{}'", scrapperConfigurationProperties.getUrl());
  }

  @Bean
  public RetryTemplate getRetryTemplate() {
    RetryTemplate retryTemplate = new RetryTemplate();
    retryTemplate.setRetryPolicy(new SimpleRetryPolicy());
    return retryTemplate;
  }

  @Bean
  public RateLimiter scrapperRateLimiter() {
    double permitsPerSec = scrapperConfigurationProperties.getRequestPerMinute() / 60d;
    log.info("scrapper api permitsPerSec: {}", permitsPerSec);
    return RateLimiter.create(permitsPerSec);
  }

  @Bean
  public RestClient scrapperApiRestClient() {
    return RestClient.builder()
        .baseUrl(scrapperConfigurationProperties.getUrl())
        .defaultHeader(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.5845.188 Safari/537.36")
        .requestInterceptor(
            (request, body, execution) -> {
              if (scrapperConfigurationProperties.getIsDebugEnabled()) {
                log.info("Scrapper api url: {}", request.getURI());
              }
              return execution.execute(request, body);
            })
        .build();
  }

  private Map<String, String> getDefaultUriVariables() {
    Map<String, String> defaultUriVariables = new HashMap<>();
    // api key can be null for free tier
    if (scrapperConfigurationProperties.getApiKey() != null) {
      defaultUriVariables.put("api_key", scrapperConfigurationProperties.getApiKey());
    }
    // use render for return all HTML dom
    defaultUriVariables.put("render", "true");

    // premium option is better but consume 10 credits instead of 1, use this when API key is available
    if (StringUtils.hasLength(scrapperConfigurationProperties.getApiKey())) {
      defaultUriVariables.put("premium", "true");
    }
    
    log.debug("Default URI variables: {}", defaultUriVariables);
    return defaultUriVariables;
  }

  @Data
  @Configuration
  @ConfigurationProperties(prefix = "scrapper")
  public static class ScrapperConfigurationProperties {
    private String url;
    private String apiKey;
    private Boolean premium;
    private Integer requestPerMinute = 60;
    private Boolean isDebugEnabled = false;
  }
}
