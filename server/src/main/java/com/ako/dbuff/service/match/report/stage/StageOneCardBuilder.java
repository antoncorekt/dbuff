package com.ako.dbuff.service.match.report.stage;

import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.discord.icon.IconService;
import com.ako.dbuff.service.match.DotaApiLastMatchesService.PlayerLine;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The basic card of a new match thread: one line per tracked player from the recent-matches list.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StageOneCardBuilder {
  private static final String SEPARATOR = " · ";
  private static final String UNKNOWN_HERO = "unknown hero";
  private static final String UNKNOWN_NUMBER = "?";
  private static final String UNKNOWN_PLAYER = "Player";

  private final ConstantsManagers constants;
  private final IconService icons;

  /** The card, or null when no tracked player's line is known. */
  public SectionView build(MatchFacts facts, List<PlayerLine> lines, Map<Long, String> names) {
    if (lines.isEmpty()) {
      return null;
    }
    Map<String, HeroConstant> heroes = heroes();
    List<String> bullets = lines.stream().map(line -> bullet(line, names, heroes)).toList();
    return new SectionView(
        "Players", null, facts.accent(), bullets, "kills/deaths/assists · gold/xp per minute");
  }

  private String bullet(
      PlayerLine line, Map<Long, String> names, Map<String, HeroConstant> heroes) {
    String name = names.getOrDefault(line.accountId(), UNKNOWN_PLAYER);
    return String.join(
        SEPARATOR,
        name,
        heroName(line.heroId(), heroes),
        number(line.kills()) + "/" + number(line.deaths()) + "/" + number(line.assists()),
        number(line.goldPerMin()) + "/" + number(line.xpPerMin()),
        result(line.won()));
  }

  private String heroName(Long heroId, Map<String, HeroConstant> heroes) {
    HeroConstant hero = heroId == null ? null : heroes.get(String.valueOf(heroId));
    return hero == null || hero.getLocalized_name() == null
        ? UNKNOWN_HERO
        : icons.hero(heroId) + hero.getLocalized_name();
  }

  private static String number(Long value) {
    return value == null ? UNKNOWN_NUMBER : String.valueOf(value);
  }

  private static String result(Boolean won) {
    if (won == null) {
      return "result unknown";
    }
    return won ? "Win" : "Loss";
  }

  private Map<String, HeroConstant> heroes() {
    try {
      return constants.getHeroConstantMap();
    } catch (RuntimeException e) {
      log.warn("Hero names unavailable for the match card: {}", e.getMessage());
      return Map.of();
    }
  }
}
