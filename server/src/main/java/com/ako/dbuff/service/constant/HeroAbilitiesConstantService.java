package com.ako.dbuff.service.constant;

import com.ako.dbuff.config.DotaApiConstant;
import com.ako.dbuff.dao.repo.ConstantDomainRepo;
import com.ako.dbuff.service.constant.data.HeroesAbilityConstant;
import org.springframework.stereotype.Service;

@Service
public class HeroAbilitiesConstantService extends BaseApiConstantService<HeroesAbilityConstant> {

  protected HeroAbilitiesConstantService(
      ConstantDomainRepo constantDomainRepo, ConstantFetcherService constantFetcherService) {
    super(constantDomainRepo, constantFetcherService);
  }

  @Override
  public DotaApiConstant getConstantId() {
    return DotaApiConstant.HERO_ABILITIES;
  }
}
