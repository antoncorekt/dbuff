package com.ako.dbuff.service.constant;

import com.ako.dbuff.config.DotaApiConstant;
import com.ako.dbuff.dao.repo.ConstantDomainRepo;
import com.ako.dbuff.service.constant.data.AbilityConstant;
import org.springframework.stereotype.Service;

@Service
public class AbilitiesConstantService extends BaseApiConstantService<AbilityConstant> {

  protected AbilitiesConstantService(
      ConstantDomainRepo constantDomainRepo, ConstantFetcherService constantFetcherService) {
    super(constantDomainRepo, constantFetcherService);
  }

  @Override
  public DotaApiConstant getConstantId() {
    return DotaApiConstant.ABILITIES;
  }
}
