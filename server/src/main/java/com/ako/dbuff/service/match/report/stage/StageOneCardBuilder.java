package com.ako.dbuff.service.match.report.stage;

import com.ako.dbuff.service.match.DotaApiLastMatchesService.PlayerLine;
import com.ako.dbuff.service.replay.report.ReportHeroNames;
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

  private final ReportHeroNames heroNames;

  /** The card, or null when no tracked player's line is known. */
  public SectionView build(MatchFacts facts, List<PlayerLine> lines, Map<Long, String> names) {
    if (lines.isEmpty()) {
      return null;
    }
    List<String> bullets = lines.stream().map(line -> bullet(line, names)).toList();
    return new SectionView(
        "Players", null, facts.accent(), bullets, "kills/deaths/assists · gold/xp per minute");
  }

  private String bullet(PlayerLine line, Map<Long, String> names) {
    String name = names.getOrDefault(line.accountId(), UNKNOWN_PLAYER);
    return String.join(
        SEPARATOR,
        name,
        heroName(line.heroId()),
        number(line.kills()) + "/" + number(line.deaths()) + "/" + number(line.assists()),
        number(line.goldPerMin()) + "/" + number(line.xpPerMin()),
        result(line.won()));
  }

  private String heroName(Long heroId) {
    if (heroId == null) {
      return UNKNOWN_HERO;
    }
    try {
      return heroNames.known(heroId).orElse(UNKNOWN_HERO);
    } catch (RuntimeException e) {
      log.warn("Hero name unavailable for the match card: {}", e.getMessage());
      return UNKNOWN_HERO;
    }
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
}
