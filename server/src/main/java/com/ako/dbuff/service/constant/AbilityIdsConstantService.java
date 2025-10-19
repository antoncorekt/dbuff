package com.ako.dbuff.service.constant;

import com.ako.dbuff.config.DotaApiConstant;
import com.ako.dbuff.dao.repo.ConstantDomainRepo;
import com.ako.dbuff.service.constant.data.AbilityIdsConstant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AbilityIdsConstantService extends BaseApiConstantService<AbilityIdsConstant> {

  protected AbilityIdsConstantService(
      ConstantDomainRepo constantDomainRepo, ConstantFetcherService constantFetcherService) {
    super(constantDomainRepo, constantFetcherService);
  }

  @Override
  public DotaApiConstant getConstantId() {
    return DotaApiConstant.ABILITIES_IDS;
  }

  protected Map<String, AbilityIdsConstant> convert(Map<String, Object> resp) {
    Map<String, AbilityIdsConstant> res = new HashMap<>();

    resp.forEach(
        (key, value) -> {
          String[] id = key.split(",");
          Arrays.stream(id)
              .map(Long::parseLong)
              .forEach(
                  l ->
                      res.put(
                          l.toString(),
                          new AbilityIdsConstant(
                              l, (String) ((Map<String, Object>) value).get("name"))));
        });

    return res;
  }
}
