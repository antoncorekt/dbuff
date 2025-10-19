package com.ako.dbuff.service.constant;

import com.ako.dbuff.config.DotaApiConstant;
import com.ako.dbuff.dao.repo.ConstantDomainRepo;
import com.ako.dbuff.dotapi.model.GetConstantsByResource200Response;
import com.ako.dbuff.dotapi.model.GetConstantsByResource200ResponseOneOfInner;
import com.ako.dbuff.service.constant.data.PatchConstant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PatchConstantService extends BaseApiConstantService<PatchConstant> {

  protected PatchConstantService(
      ConstantDomainRepo constantDomainRepo, ConstantFetcherService constantFetcherService) {
    super(constantDomainRepo, constantFetcherService);
  }

  @Override
  public Map<String, PatchConstant> convert(GetConstantsByResource200Response resp) {
    List<GetConstantsByResource200ResponseOneOfInner> list =
        resp.getListGetConstantsByResource200ResponseOneOfInner();

    return list.stream()
        .map(x -> objectMapper.convertValue(x, PatchConstant.class))
        .collect(Collectors.toMap(x -> String.valueOf(x.getId()), Function.identity()));
  }

  @Override
  public DotaApiConstant getConstantId() {
    return DotaApiConstant.PATCH;
  }
}
