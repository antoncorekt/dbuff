package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import java.util.HashMap;
import java.util.Map;
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

  /** The stored hero name; the replay's entity name, split into words, where it is not known. */
  public Map<Long, String> bySlot(long matchId) {
    Map<Long, String> names = new HashMap<>();
    for (ReplayPlayerDomain p : replayPlayerRepo.findByMatchId(matchId)) {
      if (p.getPlayerSlot() != null && p.getHeroEntityName() != null) {
        names.put(p.getPlayerSlot(), Prettify.entity(p.getHeroEntityName()));
      }
    }
    for (PlayerMatchStatisticDomain s : statRepo.findAllByMatchId(matchId)) {
      HeroConstant hero = s.getHeroId() == null ? null : heroKits.hero(s.getHeroId()).orElse(null);
      if (hero != null && hero.getLocalized_name() != null && s.getPlayerSlot() != null) {
        names.put(s.getPlayerSlot(), hero.getLocalized_name());
      }
    }
    return names;
  }
}
