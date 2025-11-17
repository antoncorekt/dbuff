package com.ako.dbuff.service.constant;

import com.ako.dbuff.config.DotaApiConstant;
import com.ako.dbuff.dao.repo.ConstantDomainRepo;
import com.ako.dbuff.service.constant.data.AbilityIdsConstant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
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
          try {
            String abilityName =
                value instanceof String
                    ? (String) value
                    : objectMapper.convertValue(value, AbilityIdsConstant.class).getName();

            // Handle comma-separated IDs like "3060,1617": "templar_assassin_hidden_gates"
            String[] ids = key.split(",");
            Arrays.stream(ids)
                .map(String::trim)
                .forEach(
                    idStr -> {
                      try {
                        Long abilityId = Long.parseLong(idStr);
                        res.put(
                            abilityId.toString(), new AbilityIdsConstant(abilityId, abilityName));
                      } catch (NumberFormatException e) {
                        log.warn("Skipping invalid ability ID: {} in key: {}", idStr, key);
                      }
                    });
          } catch (Exception e) {
            log.error("Convert failed to key: {}", key, e);
          }
        });

    return res;
  }
}
