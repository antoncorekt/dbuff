package com.ako.dbuff.service.replay.analysis.skillstat;

import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.replay.AbilityNames;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import com.ako.dbuff.service.replay.report.AbilityLabels;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConstantSkillStatNames implements SkillStatNames {
  private final HeroKits heroKits;
  private final AbilityNames abilityNames;
  private final AbilityLabels abilityLabels;

  @Override
  public String hero(long heroId) {
    return heroKits
        .hero(heroId)
        .map(HeroConstant::getLocalized_name)
        .filter(name -> !name.isBlank())
        .orElse("hero " + heroId);
  }

  @Override
  public String skill(long abilityId) {
    return abilityNames
        .nameOf(abilityId)
        .map(abilityLabels::label)
        .filter(name -> !name.isBlank())
        .orElse("skill " + abilityId);
  }
}
