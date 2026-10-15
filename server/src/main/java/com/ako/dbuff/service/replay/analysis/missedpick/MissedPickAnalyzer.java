package com.ako.dbuff.service.replay.analysis.missedpick;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import com.ako.dbuff.dao.repo.AbilityRepo;
import com.ako.dbuff.dao.repo.FightAbilityUseRepo;
import com.ako.dbuff.dao.repo.FightImportanceRepo;
import com.ako.dbuff.dao.repo.MissedPickRepo;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores a {@code missed_pick} row for each skill a player took while a clearly stronger one of the
 * same slot kind was still free and went to the enemy later: it did more total damage in the match
 * and was cast in an important fight its team won. Reads the stored draft, the match's ability
 * damage and the fight tables only. The candidate's own 12-month average damage is not stored
 * anywhere yet, so it is not part of the row.
 */
@Slf4j
@Order(97)
@Component
@RequiredArgsConstructor
public class MissedPickAnalyzer implements ReplayAnalyzer {
  private final AbilityDraftPickRepo pickRepo;
  private final AbilityRepo abilityRepo;
  private final FightImportanceRepo importanceRepo;
  private final FightAbilityUseRepo abilityUseRepo;
  private final MissedPickRepo missedPickRepo;
  private final MissedPickProperties properties;
  private final AbilityDraftProfile draftProfile;

  @Override
  public String name() {
    return "missed-pick";
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  public boolean supports(GameModeProfile mode) {
    return mode.gameModeId() == draftProfile.gameModeId();
  }

  @Override
  public Set<String> requires() {
    return Set.of("ability-draft", "important-fights", "fight-abilities");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    List<AbilityDraftPickDomain> picks = pickRepo.findByMatchId(matchId);
    long usable = picks.stream().filter(MissedPickFinder::complete).count();
    if (usable < picks.size()) {
      log.info(
          "Match {}: {} of {} draft picks lack a turn number or other field and are skipped",
          matchId,
          picks.size() - usable,
          picks.size());
    }
    if (usable == 0) {
      return;
    }
    List<AbilityDomain> abilities = abilityRepo.findAllByMatchId(matchId);
    if (abilities.isEmpty()) {
      log.info("Match {} has no stored ability damage, so no missed picks", matchId);
      return;
    }
    Map<String, Long> damage = new HashMap<>();
    abilities.forEach(
        a ->
            damage.merge(
                key(a.getPlayerSlot(), a.getAbilityId()), orZero(a.getDamageDealt()), Long::sum));
    WonFightCasts casts =
        new WonFightCasts(
            importanceRepo.findByMatchId(matchId), abilityUseRepo.findByMatchId(matchId));
    missedPickRepo.saveAll(
        new MissedPickFinder(properties.getMinRatio())
            .find(
                matchId,
                picks,
                (slot, abilityId) -> damage.getOrDefault(key(slot, abilityId), 0L),
                casts,
                version()));
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    missedPickRepo.deleteByMatchId(matchId);
  }

  private static String key(Long slot, Long abilityId) {
    return slot + "/" + abilityId;
  }

  private static long orZero(Long value) {
    return value == null ? 0 : value;
  }
}
