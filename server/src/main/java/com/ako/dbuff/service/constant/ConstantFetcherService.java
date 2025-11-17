package com.ako.dbuff.service.constant;

import com.ako.dbuff.config.CacheConfig;
import com.ako.dbuff.config.DotaApiConstant;
import com.ako.dbuff.dotapi.api.ConstantsApi;
import com.ako.dbuff.dotapi.invoker.ApiException;
import com.ako.dbuff.dotapi.model.GetConstantsByResource200Response;
import com.google.common.util.concurrent.RateLimiter;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class ConstantFetcherService {

  private final ConstantsApi constantsApi;
  private final RateLimiter dotaApiRateLimiter;

  @Cacheable(value = CacheConfig.DOTA_CONSTANTS_CACHE)
  public GetConstantsByResource200Response fetch(DotaApiConstant dotaApiConstant) {
    GetConstantsByResource200Response resp = null;
    try {
      log.debug(
          "Method called with parameter: {} (name: {}, hashCode: {})",
          dotaApiConstant,
          dotaApiConstant.getName(),
          dotaApiConstant.hashCode());
      log.info(
          "Executing fetch method for constant {} - this means cache miss or first call",
          dotaApiConstant);
      dotaApiRateLimiter.acquire();
      log.info("Fetching constant from dota api constant {}", dotaApiConstant);
      resp = constantsApi.getConstantsByResource(dotaApiConstant.getName());
      log.info(
          "Successfully fetched constant for resource {} - will be cached for future calls",
          dotaApiConstant);
    } catch (ApiException e) {
      log.error("Failed to fetch constant for resource {}: {}", dotaApiConstant, e.getMessage(), e);
      // todo custom exception
      throw new RuntimeException(e);
    }

    return resp;
  }
}
