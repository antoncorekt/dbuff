package com.ako.dbuff.service.replay;

import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.AbilityIdsConstant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConstantAbilityNames implements AbilityNames {
  private final ConstantsManagers constants;

  @Override
  public Optional<String> nameOf(long abilityId) {
    AbilityIdsConstant constant = constants.getAbilityConstantMap().get(String.valueOf(abilityId));
    return Optional.ofNullable(constant).map(AbilityIdsConstant::getName);
  }
}
