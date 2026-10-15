package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.discord.icon.IconService;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The heroes of a match by player slot, as players call them; a slot with no known hero is absent.
 */
@Component
@RequiredArgsConstructor
public class ReportHeroNames {
  private final PlayerGameStatisticRepo statRepo;
  private final ReplayPlayerRepo replayPlayerRepo;
  private final HeroKits heroKits;
  private final IconService icons;

  /** "Name (Hero)", or the name alone where the hero is not known. */
  public static String label(String name, String hero) {
    return hero == null || hero.isBlank() ? name : name + " (" + hero + ")";
  }

  /** The stored hero name; the replay's entity name, split into words, where it is not known. */
  public Map<Long, String> bySlot(long matchId) {
    return names(matchId, stat -> "");
  }

  /** {@link #bySlot} with the hero icon before each name that has a known hero, for reports. */
  public Map<Long, String> iconBySlot(long matchId) {
    return names(matchId, stat -> stat.getHeroId() == null ? "" : icons.hero(stat.getHeroId()));
  }

  private Map<Long, String> names(
      long matchId, Function<PlayerMatchStatisticDomain, String> iconOf) {
    Map<Long, String> names = new HashMap<>();
    for (ReplayPlayerDomain p : replayPlayerRepo.findByMatchId(matchId)) {
      if (p.getPlayerSlot() != null && p.getHeroEntityName() != null) {
        names.put(p.getPlayerSlot(), Prettify.entity(p.getHeroEntityName()));
      }
    }
    for (PlayerMatchStatisticDomain s : statRepo.findAllByMatchId(matchId)) {
      HeroConstant hero = s.getHeroId() == null ? null : heroKits.hero(s.getHeroId()).orElse(null);
      if (hero != null && hero.getLocalized_name() != null && s.getPlayerSlot() != null) {
        names.put(s.getPlayerSlot(), iconOf.apply(s) + hero.getLocalized_name());
      }
    }
    return names;
  }

  /** The name of a hero by its OpenDota id, with its icon before it. */
  public String heroName(long heroId) {
    return known(heroId).orElse("Hero " + heroId);
  }

  /** {@link #heroName} where the hero is known with a name, empty otherwise. */
  public Optional<String> known(long heroId) {
    return heroKits
        .hero(heroId)
        .filter(hero -> hero.getLocalized_name() != null)
        .map(hero -> icons.hero(heroId) + hero.getLocalized_name());
  }
}
