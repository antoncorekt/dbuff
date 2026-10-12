package com.ako.dbuff.service.constant;

import com.ako.dbuff.config.ObjectMapperConfig;
import com.ako.dbuff.dao.model.ConstantDomain;
import com.ako.dbuff.dao.repo.ConstantDomainRepo;
import com.ako.dbuff.dotapi.model.GetConstantsByResource200Response;
import com.ako.dbuff.service.constant.data.ConstantData;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class BaseApiConstantService<T extends ConstantData> implements ConstantRepo<T> {

  private final ConstantDomainRepo constantDomainRepo;

  private final Class<T> clazz;

  private final ConstantFetcherService constantFetcherService;
  protected final ObjectMapper objectMapper;
  private final AtomicBoolean refreshFailureLogged = new AtomicBoolean();

  protected BaseApiConstantService(
      ConstantDomainRepo constantDomainRepo, ConstantFetcherService constantFetcherService) {
    this.constantDomainRepo = constantDomainRepo;
    this.constantFetcherService = constantFetcherService;
    objectMapper = ObjectMapperConfig.defaultObjectMapper();
    Type superClass = getClass().getGenericSuperclass();
    this.clazz = (Class<T>) ((ParameterizedType) superClass).getActualTypeArguments()[0];
  }

  /**
   * Shape version of the converted data. Raise it when the constant class gains fields, so rows
   * stored in the older shape are fetched again. Rows without a version count as 0.
   */
  protected int schemaVersion() {
    return 0;
  }

  @Override
  public Map<String, T> getConstantMap() {

    Optional<ConstantDomain> domainConstant =
        constantDomainRepo.findById(getConstantId().getName());

    if (domainConstant.isEmpty()) {
      return fetchAndStore();
    }

    ConstantDomain stored = domainConstant.get();
    int storedVersion = stored.getSchemaVersion() == null ? 0 : stored.getSchemaVersion();
    if (storedVersion < schemaVersion()) {
      try {
        return fetchAndStore();
      } catch (RuntimeException e) {
        if (refreshFailureLogged.compareAndSet(false, true)) {
          log.warn(
              "Refreshing constant {} to schema version {} failed, serving the stored version {}",
              getConstantId(),
              schemaVersion(),
              storedVersion,
              e);
        }
      }
    }
    return convert(stored.getConstantValue());
  }

  private Map<String, T> fetchAndStore() {
    GetConstantsByResource200Response resp = constantFetcherService.fetch(getConstantId());

    Map<String, T> convertedMap = convert(resp);

    constantDomainRepo.save(
        ConstantDomain.builder()
            .constantName(getConstantId().getName())
            .constantValue((Map) convertedMap)
            .schemaVersion(schemaVersion())
            .build());

    return convertedMap;
  }

  protected Map<String, T> convert(GetConstantsByResource200Response resource200Response) {
    return convert(resource200Response.getMapStringObject());
  }

  protected Map<String, T> convert(Map<String, Object> resp) {
    Map<String, T> result = new HashMap<>();

    resp.forEach(
        (k, v) -> {
          Map<String, Object> val = (Map<String, Object>) v;
          result.put(k, objectMapper.convertValue(val, clazz));
        });

    return result;
  }
}
