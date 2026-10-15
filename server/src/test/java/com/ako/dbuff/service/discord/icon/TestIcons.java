package com.ako.dbuff.service.discord.icon;

import com.ako.dbuff.dao.model.DiscordIconDomain;
import com.ako.dbuff.dao.repo.DiscordIconRepo;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.HeroConstant;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** An {@link IconService} over fixed data, for tests of the code that puts icons into labels. */
public final class TestIcons {
  private TestIcons() {}

  public static IconService off() {
    return service(false, List.of());
  }

  /** Switched on, with hero 1 (antimage), the ability antimage_blink and the item blink synced. */
  public static IconService on() {
    return service(
        true,
        List.of(
            row(IconKind.HERO, "antimage", 11L, "h_antimage"),
            row(IconKind.ITEM, "blink", 12L, "i_blink"),
            row(IconKind.ABILITY, "antimage_blink", 13L, "a_antimage_blink")));
  }

  public static String heroMention() {
    return "<:h_antimage:11> ";
  }

  public static String itemMention() {
    return "<:i_blink:12> ";
  }

  public static String abilityMention() {
    return "<:a_antimage_blink:13> ";
  }

  private static IconService service(boolean enabled, List<DiscordIconDomain> rows) {
    IconProperties properties = new IconProperties();
    properties.setEnabled(enabled);
    DiscordIconRepo repo = mock(DiscordIconRepo.class);
    ConstantsManagers constants = mock(ConstantsManagers.class);
    when(repo.findByStatus(IconStatus.SYNCED)).thenReturn(rows);
    when(constants.getHeroConstantMap())
        .thenReturn(Map.of("1", new HeroConstant("1", "npc_dota_hero_antimage", "Anti-Mage")));
    return new IconService(properties, repo, constants, new SimpleMeterRegistry());
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
