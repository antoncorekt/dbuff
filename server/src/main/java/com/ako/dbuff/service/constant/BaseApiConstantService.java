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

public abstract class BaseApiConstantService<T extends ConstantData> implements ConstantRepo<T> {

  private final ConstantDomainRepo constantDomainRepo;

  private final Class<T> clazz;

  private final ConstantFetcherService constantFetcherService;
  protected final ObjectMapper objectMapper;

  protected BaseApiConstantService(
      ConstantDomainRepo constantDomainRepo, ConstantFetcherService constantFetcherService) {
    this.constantDomainRepo = constantDomainRepo;
    this.constantFetcherService = constantFetcherService;
    objectMapper = ObjectMapperConfig.defaultObjectMapper();
    Type superClass = getClass().getGenericSuperclass();
    this.clazz = (Class<T>) ((ParameterizedType) superClass).getActualTypeArguments()[0];
  }

  @Override
  public Map<String, T> getConstantMap() {

    Optional<ConstantDomain> domainConstant =
        constantDomainRepo.findById(getConstantId().getName());

    if (domainConstant.isPresent()) {
      return convert(domainConstant.get().getConstantValue());
    }

    GetConstantsByResource200Response resp = constantFetcherService.fetch(getConstantId());

    Map<String, T> convertedMap = convert(resp);

    constantDomainRepo.save(
        ConstantDomain.builder()
            .constantName(getConstantId().getName())
            .constantValue((Map) convertedMap)
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
