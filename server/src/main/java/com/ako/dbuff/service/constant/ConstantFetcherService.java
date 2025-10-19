package com.ako.dbuff.service.constant;

import com.ako.dbuff.config.DotaApiConstant;
import com.ako.dbuff.dotapi.api.ConstantsApi;
import com.ako.dbuff.dotapi.invoker.ApiException;
import com.ako.dbuff.dotapi.model.GetConstantsByResource200Response;
import com.google.common.util.concurrent.RateLimiter;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class ConstantFetcherService {

  private final ConstantsApi constantsApi;
  private final RateLimiter dotaApiRateLimiter;

  public GetConstantsByResource200Response fetch(DotaApiConstant dotaApiConstant) {
    GetConstantsByResource200Response resp = null;
    try {
      log.info("Trying to fetch constant by resource {}.", dotaApiConstant);
      dotaApiRateLimiter.acquire();
      log.info("Fetching constant from dota api constant {}", dotaApiConstant);
      resp = constantsApi.getConstantsByResource(dotaApiConstant.getName());
    } catch (ApiException e) {
      log.error(e.getMessage(), e);
      // todo custom exception
      throw new RuntimeException(e);
    }

    return resp;
  }
}
