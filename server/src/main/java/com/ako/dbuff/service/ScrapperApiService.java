package com.ako.dbuff.service;

import com.ako.dbuff.config.ScrapperConfig;
import com.google.common.util.concurrent.RateLimiter;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
            // Build the full URI manually to avoid double-encoding issues
            URI uri = buildScrapperUri(targetUrl, r);
            log.debug("Final scrapper URI: {}", uri);

            // Perform GET request
            String html = scrapperApiRestClient.get().uri(uri).retrieve().body(String.class);

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

  /**
   * Builds the scrapper API URI manually to ensure proper URL encoding. The target URL must be
   * URL-encoded as a single parameter value, while the other parameters (render, api_key, premium)
   * are added normally.
   */
  private URI buildScrapperUri(String targetUrl, RetryContext retryContext) {
    StringBuilder sb = new StringBuilder();

    // URL-encode the target URL so it becomes a single parameter value
    String encodedTargetUrl = URLEncoder.encode(targetUrl, StandardCharsets.UTF_8);

    // Start with the url parameter
    sb.append("?url=").append(encodedTargetUrl);

    // Add render parameter for full HTML DOM
    sb.append("&render=true");

    // Add API key if available
    if (StringUtils.hasLength(scrapperConfigurationProperties.getApiKey())) {
      sb.append("&api_key=").append(scrapperConfigurationProperties.getApiKey());
      log.debug("Added api_key parameter to request");
    } else {
      log.debug("No API key available, using free tier");
    }

    // Override premium on retry if needed
    if (retryContext.getRetryCount() > 1) {
      log.debug("Retry attempt {}, ensuring premium=true", retryContext.getRetryCount());
      sb.append("&premium=true");
    }

    // Create URI from base URL + query string
    String baseUrl = scrapperConfigurationProperties.getUrl();
    return URI.create(baseUrl + sb.toString());
  }
}
