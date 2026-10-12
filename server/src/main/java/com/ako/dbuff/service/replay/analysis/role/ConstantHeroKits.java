package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConstantHeroKits implements HeroKits {
  private final ConstantsManagers constants;

  @Override
  public Optional<HeroConstant> hero(long heroId) {
    return Optional.ofNullable(constants.getHeroConstantMap().get(String.valueOf(heroId)));
  }

  @Override
  public Optional<AbilityConstant> ability(String name) {
    return Optional.ofNullable(constants.getAllAbilityConstants().get(name));
  }
}
