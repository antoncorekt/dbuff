package com.ako.dbuff.service.scrapper;

import com.ako.dbuff.config.ScrapperConfig.ScrapperConfigurationProperties;
import com.google.common.util.concurrent.RateLimiter;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Slf4j
public class ScraperApiProvider implements ScrapperProvider {

  private final RateLimiter scrapperRateLimiter;
  private final RestClient scrapperApiRestClient;
  private final RetryTemplate retryTemplate;
  private final ScrapperConfigurationProperties scrapperConfigurationProperties;

  public ScraperApiProvider(
      RateLimiter scrapperRateLimiter,
      RestClient scrapperApiRestClient,
      RetryTemplate retryTemplate,
      ScrapperConfigurationProperties scrapperConfigurationProperties) {
    this.scrapperRateLimiter = scrapperRateLimiter;
    this.scrapperApiRestClient = scrapperApiRestClient;
    this.retryTemplate = retryTemplate;
    this.scrapperConfigurationProperties = scrapperConfigurationProperties;
  }

  @Override
  public Document scrap(String targetUrl) {
    return retryTemplate.execute(
        r -> {
          log.info("Trying to scrap url: {}", targetUrl);
          scrapperRateLimiter.acquire();
          log.info("Start scrap url {}", targetUrl);

          try {
            URI uri = buildScrapperUri(targetUrl, r);
            log.debug("Final scrapper URI: {}", uri);

            String html = scrapperApiRestClient.get().uri(uri).retrieve().body(String.class);
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

  @Override
  public String name() {
    return "scraperapi";
  }

  private URI buildScrapperUri(
      String targetUrl, org.springframework.retry.RetryContext retryContext) {
    StringBuilder sb = new StringBuilder();
    String encodedTargetUrl = URLEncoder.encode(targetUrl, StandardCharsets.UTF_8);
    sb.append("?url=").append(encodedTargetUrl);
    sb.append("&render=true");

    if (StringUtils.hasLength(scrapperConfigurationProperties.getApiKey())) {
      sb.append("&api_key=").append(scrapperConfigurationProperties.getApiKey());
      log.debug("Added api_key parameter to request");
    } else {
      log.debug("No API key available, using free tier");
    }

    if (retryContext.getRetryCount() > 1) {
      log.debug("Retry attempt {}, ensuring premium=true", retryContext.getRetryCount());
      sb.append("&premium=true");
    }

    String baseUrl = scrapperConfigurationProperties.getUrl();
    return URI.create(baseUrl + sb.toString());
  }
}
