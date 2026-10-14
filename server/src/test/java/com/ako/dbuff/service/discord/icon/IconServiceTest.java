package com.ako.dbuff.service.discord.icon;

import com.ako.dbuff.dao.model.DiscordIconDomain;
import com.ako.dbuff.dao.repo.DiscordIconRepo;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.HeroConstant;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IconServiceTest {
  private final IconProperties properties = new IconProperties();
  private final DiscordIconRepo repo = mock(DiscordIconRepo.class);
  private final ConstantsManagers constants = mock(ConstantsManagers.class);
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final IconService service = new IconService(properties, repo, constants, registry);

  @BeforeEach
  void setUp() {
    when(constants.getHeroConstantMap())
        .thenReturn(Map.of("1", new HeroConstant("1", "npc_dota_hero_antimage", "Anti-Mage")));
    when(repo.findByStatus(IconStatus.SYNCED))
        .thenReturn(
            List.of(
                row(IconKind.HERO, "antimage", 11L, "h_antimage"),
                row(IconKind.ITEM, "blink", 12L, "i_blink"),
                row(IconKind.ABILITY, "antimage_blink", 13L, "a_antimage_blink")));
  }

  @Test
  void switchedOff_returnsNothingAndTouchesNoData() {
    assertThat(service.hero(1)).isEmpty();
    assertThat(service.ability("antimage_blink")).isEmpty();
    assertThat(service.ability("item_blink")).isEmpty();
    assertThat(service.item("blink")).isEmpty();

    verifyNoInteractions(repo, constants);
    assertThat(registry.getMeters()).isEmpty();
  }

  @Test
  void syncedEmoji_isTheFirstChoice() {
    properties.setEnabled(true);

    assertThat(service.hero(1)).isEqualTo("<:h_antimage:11> ");
    assertThat(service.item("blink")).isEqualTo("<:i_blink:12> ");
    assertThat(service.ability("antimage_blink")).isEqualTo("<:a_antimage_blink:13> ");
    assertThat(registry.getMeters()).isEmpty();
  }

  @Test
  void itemNameInAbilityPosition_isLookedUpAsItem() {
    properties.setEnabled(true);

    assertThat(service.ability("item_blink")).isEqualTo("<:i_blink:12> ");
  }

  @Test
  void notSynced_fallsBackToPlaceholderOfTheKindAndCounts() {
    properties.setEnabled(true);

    assertThat(service.hero(999)).isEqualTo(IconKind.HERO.getPlaceholder() + " ");
    assertThat(service.ability("special_bonus_x"))
        .isEqualTo(IconKind.ABILITY.getPlaceholder() + " ");
    assertThat(service.item("unknown")).isEqualTo(IconKind.ITEM.getPlaceholder() + " ");
    assertThat(registry.counter(IconService.FALLBACK_METER, "kind", "HERO").count()).isEqualTo(1);
    assertThat(registry.counter(IconService.FALLBACK_METER, "kind", "ITEM").count()).isEqualTo(1);
  }

  @Test
  void placeholdersOff_fallsBackToNothing() {
    properties.setEnabled(true);
    properties.setPlaceholders(false);

    assertThat(service.item("unknown")).isEmpty();
    assertThat(registry.counter(IconService.FALLBACK_METER, "kind", "ITEM").count()).isEqualTo(1);
  }

  @Test
  void blankOrNullAbility_isNothing() {
    properties.setEnabled(true);

    assertThat(service.ability(null)).isEmpty();
    assertThat(service.ability(" ")).isEmpty();
  }

  @Test
  void refresh_picksUpTheTableAgain() {
    properties.setEnabled(true);
    assertThat(service.item("blink")).isEqualTo("<:i_blink:12> ");

    when(repo.findByStatus(IconStatus.SYNCED)).thenReturn(List.of());
    assertThat(service.item("blink")).isEqualTo("<:i_blink:12> ");
    service.refresh();

    assertThat(service.item("blink")).isEqualTo(IconKind.ITEM.getPlaceholder() + " ");
  }

  private static DiscordIconDomain row(IconKind kind, String key, long id, String name) {
    return DiscordIconDomain.builder()
        .kind(kind)
        .iconKey(key)
        .emojiId(id)
        .emojiName(name)
        .status(IconStatus.SYNCED)
        .build();
  }
}
