package com.ako.dbuff.service;

import com.ako.dbuff.config.ScrapperConfig;
import com.google.common.util.concurrent.RateLimiter;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.retry.RetryContext;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

@Slf4j
@Service
@AllArgsConstructor
public class ScrapperApiService {

  private final RateLimiter scrapperRateLimiter;
  private final RestClient scrapperApiRestClient;
  private final RetryTemplate retryTemplate;
  private final ScrapperConfig.ScrapperConfigurationProperties scrapperConfigurationProperties;

  @Retryable
  public Document scrap(String targetUrl) {

    return retryTemplate.execute(
        r -> {
          log.info("Trying to scrap url: {}", targetUrl);
          scrapperRateLimiter.acquire();
          log.info("Start scrap url {}", targetUrl);

          try {
            // Perform GET request
            String html =
                scrapperApiRestClient
                    .get()
                    .uri(uriBuilder -> prepareUri(r, uriBuilder, targetUrl).build())
                    .retrieve()
                    .body(String.class);

            // You can now parse it with Jsoup:
            Document doc = Jsoup.parse(html);

            return doc;

          } catch (Exception e) {
            log.info("Scrap url {} failed: {}", targetUrl, e.getMessage());
            throw new RuntimeException(e);
          } finally {
            log.info("End scrap url {}", targetUrl);
          }
        });
  }

  private org.springframework.web.util.UriBuilder prepareUri(
      RetryContext retryContext, UriBuilder uriBuilder, String targetUrl) {
    // Add the target URL parameter
    uriBuilder.queryParam("url", targetUrl);
    
    // Add render parameter for full HTML DOM
    uriBuilder.queryParam("render", "true");
    
    // Add API key if available
    if (StringUtils.hasLength(scrapperConfigurationProperties.getApiKey())) {
      uriBuilder.queryParam("api_key", scrapperConfigurationProperties.getApiKey());
      log.debug("Added api_key and premium parameters to request");
    } else {
      log.debug("No API key available, using free tier");
    }
    
    // Override premium on retry if needed
    if (retryContext.getRetryCount() > 1) {
      log.debug("Retry attempt {}, ensuring premium=true", retryContext.getRetryCount());
      uriBuilder.queryParam("premium", "true");
    }
    
    return uriBuilder;
  }
}
