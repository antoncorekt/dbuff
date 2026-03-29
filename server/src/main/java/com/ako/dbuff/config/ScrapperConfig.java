package com.ako.dbuff.config;

import com.ako.dbuff.service.scrapper.ScraperApiProvider;
import com.ako.dbuff.service.scrapper.ScrapflyProvider;
import com.ako.dbuff.service.scrapper.ScrapperProvider;
import com.ako.dbuff.service.scrapper.ZenRowsProvider;
import com.google.common.util.concurrent.RateLimiter;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Slf4j
@Configuration
public class ScrapperConfig {

  private final ScrapperConfigurationProperties scrapperConfigurationProperties;

  public ScrapperConfig(ScrapperConfigurationProperties scrapperConfigurationProperties) {
    this.scrapperConfigurationProperties = scrapperConfigurationProperties;
    log.info("Scrapper enabled: {}", scrapperConfigurationProperties.getEnabled());
    log.info("Scrapper provider: {}", scrapperConfigurationProperties.getProvider());
    log.info(
        "Use api_key for scrapper service: {}",
        StringUtils.hasLength(scrapperConfigurationProperties.getApiKey()));
    log.debug("Scrapper API key value: '{}'", scrapperConfigurationProperties.getApiKey());
    log.debug("Scrapper URL: '{}'", scrapperConfigurationProperties.getUrl());
  }

  @Bean
  public RetryTemplate getRetryTemplate() {
    RetryTemplate retryTemplate = new RetryTemplate();
    retryTemplate.setRetryPolicy(new NeverRetryPolicy());
    return retryTemplate;
  }

  @Bean
  @ConditionalOnProperty(name = "scrapper.enabled", havingValue = "true", matchIfMissing = true)
  public RateLimiter scrapperRateLimiter() {
    double permitsPerSec = scrapperConfigurationProperties.getRequestPerMinute() / 60d;
    log.info("scrapper api permitsPerSec: {}", permitsPerSec);
    return RateLimiter.create(permitsPerSec);
  }

  @Bean
  @ConditionalOnProperty(name = "scrapper.enabled", havingValue = "true", matchIfMissing = true)
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

  @Bean
  @ConditionalOnProperty(name = "scrapper.enabled", havingValue = "true", matchIfMissing = true)
  public ScrapperProvider scrapperProvider(
      RateLimiter scrapperRateLimiter,
      RestClient scrapperApiRestClient,
      RetryTemplate getRetryTemplate) {
    String provider = scrapperConfigurationProperties.getProvider();
    log.info("Creating scrapper provider: {}", provider);

    if ("scraperapi".equalsIgnoreCase(provider)) {
      return new ScraperApiProvider(
          scrapperRateLimiter,
          scrapperApiRestClient,
          getRetryTemplate,
          scrapperConfigurationProperties);
    }
    if ("scrapfly".equalsIgnoreCase(provider)) {
      return new ScrapflyProvider();
    }
    if ("zenrows".equalsIgnoreCase(provider)) {
      return new ZenRowsProvider();
    }

    throw new IllegalArgumentException("Unknown scrapper provider: " + provider);
  }

  @Data
  @Configuration
  @ConfigurationProperties(prefix = "scrapper")
  public static class ScrapperConfigurationProperties {
    private Boolean enabled = true;
    private String provider = "scraperapi";
    private String url;
    private String apiKey;
    private Boolean premium;
    private Integer requestPerMinute = 60;
    private Boolean isDebugEnabled = false;
  }
}
