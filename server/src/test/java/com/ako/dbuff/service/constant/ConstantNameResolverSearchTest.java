package com.ako.dbuff.service.constant;

import com.ako.dbuff.service.constant.ConstantNameResolver.ConstantMatch;
import com.ako.dbuff.service.constant.ConstantNameResolver.Kind;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.constant.data.ItemConstant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConstantNameResolverSearchTest {

  private final ConstantsManagers managers = mock(ConstantsManagers.class);
  private final ConstantNameResolver resolver = new ConstantNameResolver(managers);

  @Test
  void fragmentFindsAnItemByDisplayName() {
    Map<String, ItemConstant> items = new LinkedHashMap<>();
    items.put("blink", ItemConstant.builder().id(1L).dname("Blink Dagger").build());
    items.put("force_staff", ItemConstant.builder().id(2L).dname("Force Staff").build());
    when(managers.getItemConstantMap()).thenReturn(items);

    List<ConstantMatch> found = resolver.search(Kind.ITEM, "BLINK", 20);

    assertThat(found).containsExactly(new ConstantMatch("1", "blink", "Blink Dagger"));
  }

  @Test
  void limitIsRespected() {
    Map<String, HeroConstant> heroes = new LinkedHashMap<>();
    for (int i = 1; i <= 5; i++) {
      heroes.put(
          "h" + i,
          HeroConstant.builder()
              .id(String.valueOf(i))
              .name("npc_dota_hero_" + i)
              .localized_name("Hero " + i)
              .build());
    }
    when(managers.getHeroConstantMap()).thenReturn(heroes);

    assertThat(resolver.search(Kind.HERO, "hero", 3)).hasSize(3);
  }
}
