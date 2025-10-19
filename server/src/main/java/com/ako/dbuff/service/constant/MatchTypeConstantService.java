package com.ako.dbuff.service.constant;

import com.ako.dbuff.config.DotaApiConstant;
import com.ako.dbuff.dao.repo.ConstantDomainRepo;
import com.ako.dbuff.service.constant.data.MatchTypeConstant;
import org.springframework.stereotype.Service;

@Service
public class MatchTypeConstantService extends BaseApiConstantService<MatchTypeConstant> {

  protected MatchTypeConstantService(
      ConstantDomainRepo constantDomainRepo, ConstantFetcherService constantFetcherService) {
    super(constantDomainRepo, constantFetcherService);
  }

  @Override
  public DotaApiConstant getConstantId() {
    return DotaApiConstant.GAME_MODE;
  }
}
