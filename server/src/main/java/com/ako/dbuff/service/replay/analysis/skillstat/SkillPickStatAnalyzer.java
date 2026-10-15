package com.ako.dbuff.service.replay.analysis.skillstat;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.SkillPickStatDomain;
import com.ako.dbuff.dao.repo.AbilityRepo;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.SkillPickStatRepo;
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
import org.springframework.transaction.annotation.Transactional;

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
  private final SkillPickStatRepo statRepo;
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

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    MatchDomain match = matchRepo.findById(matchId).orElse(null);
    if (match == null || match.getStartTime() == null) {
      log.info("Match {} has no stored start time, so no skill statistics", matchId);
      return;
    }
    Map<Long, List<Long>> skillsBySlot =
        abilityRepo.findAllByMatchId(matchId).stream()
            .collect(
                Collectors.groupingBy(
                    AbilityDomain::getPlayerSlot,
                    Collectors.mapping(AbilityDomain::getAbilityId, Collectors.toList())));
    if (skillsBySlot.isEmpty()) {
      log.info("Match {} has no stored abilities, so no skill statistics", matchId);
      return;
    }
    Instant start = Instant.ofEpochSecond(match.getStartTime());
    SkillWindow window =
        new SkillWindow(
            start.atZone(ZoneOffset.UTC).minusMonths(properties.getWindowMonths()).toEpochSecond(),
            match.getStartTime(),
            matchId);
    List<SkillPickStatDomain> rows = new ArrayList<>();
    for (PlayerMatchStatisticDomain player : playerRepo.findAllByMatchId(matchId)) {
      rows.addAll(
          calculator.forPlayer(
              matchId,
              player.getPlayerSlot(),
              player.getHeroId(),
              skillsBySlot.getOrDefault(player.getPlayerSlot(), List.of()),
              window,
              version()));
    }
    statRepo.saveAll(rows);
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    statRepo.deleteByMatchId(matchId);
  }
}
