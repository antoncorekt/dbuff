package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The real label logic over a few constants: Metamorphosis has a display name, Gods Strength not.
 */
final class TestLabels {
  private TestLabels() {}

  static ConstantAbilityLabels labels() {
    ConstantsManagers constants = mock(ConstantsManagers.class);
    when(constants.getAllAbilityConstants())
        .thenReturn(
            Map.of(
                "terrorblade_metamorphosis",
                AbilityConstant.builder().dname("Metamorphosis").build()));
    when(constants.getHeroConstantMap())
        .thenReturn(
            Map.of(
                "1", HeroConstant.builder().name("npc_dota_hero_sven").build(),
                "2", HeroConstant.builder().name("npc_dota_hero_terrorblade").build()));
    return new ConstantAbilityLabels(constants);
  }
}
