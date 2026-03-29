package com.ako.dbuff.service;

import com.ako.dbuff.config.ScrapperConfig.ScrapperConfigurationProperties;
import com.ako.dbuff.service.scrapper.ScrapperProvider;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ScrapperApiService {

  private final Optional<ScrapperProvider> scrapperProvider;
  private final boolean enabled;

  public ScrapperApiService(
      Optional<ScrapperProvider> scrapperProvider,
      ScrapperConfigurationProperties scrapperConfigurationProperties) {
    this.scrapperProvider = scrapperProvider;
    this.enabled = Boolean.TRUE.equals(scrapperConfigurationProperties.getEnabled());
    log.info(
        "ScrapperApiService initialized: enabled={}, provider={}",
        enabled,
        scrapperProvider.map(ScrapperProvider::name).orElse("none"));
  }

  public boolean isEnabled() {
    return enabled && scrapperProvider.isPresent();
  }

  public Document scrap(String targetUrl) {
    if (!isEnabled()) {
      throw new ScrapperDisabledException("Scrapper is disabled. Cannot scrape URL: " + targetUrl);
    }
    return scrapperProvider.get().scrap(targetUrl);
  }

  public static class ScrapperDisabledException extends RuntimeException {
    public ScrapperDisabledException(String message) {
      super(message);
    }
  }
}
