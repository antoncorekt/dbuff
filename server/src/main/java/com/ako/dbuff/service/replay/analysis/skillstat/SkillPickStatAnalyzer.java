package com.ako.dbuff.service.replay.analysis.skillstat;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.SkillPickStatDomain;
import com.ako.dbuff.dao.repo.AbilityRepo;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Stores, for each player of an Ability Draft match, how the hero, each skill held, the hero with
 * each skill and each pair of skills did in the Ability Draft games of the months before the match
 * (the match itself left out, no minimum number of games). It reads OpenDota's stored ability and
 * player rows and the replay is not needed. The win rate is shown as it is; the Wilson bound only
 * ranks.
 */
@Slf4j
@Order(97)
@Component
@RequiredArgsConstructor
public class SkillPickStatAnalyzer implements ReplayAnalyzer {
  private final MatchRepo matchRepo;
  private final PlayerGameStatisticRepo playerRepo;
  private final AbilityRepo abilityRepo;
  private final SkillStatCalculator calculator;
  private final SkillPickStatStore store;
  private final SkillStatProperties properties;

  @Override
  public String name() {
    return "skill-stats";
  }

  @Override
  public boolean supports(GameModeProfile mode) {
    return mode.gameModeId() == AbilityDraftProfile.MODE_ID;
  }

  @Override
  public int version() {
    return 1;
  }

  /**
   * The history counts run before and outside the transaction, so no connection is held while they
   * run; only the replacement of the match's rows is transactional.
   */
  @Override
  public void analyze(long matchId) {
    store.replace(matchId, rows(matchId));
  }

  @Override
  public void deleteByMatchId(long matchId) {
    store.replace(matchId, List.of());
  }

  private List<SkillPickStatDomain> rows(long matchId) {
    MatchDomain match = matchRepo.findById(matchId).orElse(null);
    if (match == null || match.getStartTime() == null) {
      log.info("Match {} has no stored start time, so no skill statistics", matchId);
      return List.of();
    }
    List<AbilityDomain> stored = abilityRepo.findAllByMatchId(matchId);
    List<AbilityDomain> usable =
        stored.stream()
            .filter(a -> a.getPlayerSlot() != null && a.getAbilityId() != null)
            .filter(a -> RealSkills.isSkill(a.getName()))
            .toList();
    long unusable =
        stored.stream().filter(a -> a.getPlayerSlot() == null || a.getAbilityId() == null).count();
    if (unusable > 0) {
      log.info("Match {}: {} ability rows have no slot or id and are skipped", matchId, unusable);
    }
    if (usable.isEmpty()) {
      log.info("Match {} has no stored abilities, so no skill statistics", matchId);
      return List.of();
    }
    Map<Long, List<Long>> skillsBySlot =
        usable.stream()
            .collect(
                Collectors.groupingBy(
                    AbilityDomain::getPlayerSlot,
                    Collectors.mapping(AbilityDomain::getAbilityId, Collectors.toList())));
    SkillWindow window =
        new SkillWindow(
            Instant.ofEpochSecond(match.getStartTime())
                .atZone(ZoneOffset.UTC)
                .minusMonths(properties.getWindowMonths())
                .toEpochSecond(),
            match.getStartTime(),
            matchId);
    List<SkillPickStatDomain> rows = new ArrayList<>();
    for (PlayerMatchStatisticDomain player : playerRepo.findAllByMatchId(matchId)) {
      if (player.getPlayerSlot() == null) {
        log.info("Match {} has a player row with no slot, skipped", matchId);
        continue;
      }
      rows.addAll(
          calculator.forPlayer(
              matchId,
              player.getPlayerSlot(),
              player.getHeroId(),
              skillsBySlot.getOrDefault(player.getPlayerSlot(), List.of()),
              window,
              version()));
    }
    return rows;
  }
}
