package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.DeathAnalysisDomain;
import com.ako.dbuff.dao.model.FightAbilityUseDomain;
import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.dao.model.TeamCompositionDomain;
import com.ako.dbuff.dao.repo.DeathAnalysisRepo;
import com.ako.dbuff.dao.repo.FightAbilityUseRepo;
import com.ako.dbuff.dao.repo.FightPlayerRepo;
import com.ako.dbuff.dao.repo.FightReasonRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.PlayerRoleRepo;
import com.ako.dbuff.dao.repo.TeamCompositionRepo;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.StoredMatchModes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * For each fight, collects the reasons of every {@link ReasonContributor} that supports the match's
 * mode into {@code fight_reason}, from what the fight, ability and death analyzers stored. Where
 * two contributors give the same code for the same subject, the more severe one is kept.
 */
@Order(32)
@Component
@RequiredArgsConstructor
public class FightReasonAnalyzer implements ReplayAnalyzer {
  private final List<ReasonContributor> contributors;
  private final StoredMatchModes modes;
  private final FightRepo fightRepo;
  private final FightPlayerRepo fightPlayerRepo;
  private final FightAbilityUseRepo abilityUseRepo;
  private final DeathAnalysisRepo deathRepo;
  private final PlayerRoleRepo roleRepo;
  private final TeamCompositionRepo compositionRepo;
  private final FightReasonRepo reasonRepo;

  @Override
  public String name() {
    return "fight-reasons";
  }

  @Override
  public int version() {
    return 2;
  }

  @Override
  public Set<String> requires() {
    return Set.of("fights", "fight-abilities", "deaths");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    GameModeProfile mode = modes.activeFor(matchId).orElse(null);
    if (mode == null) {
      return;
    }
    List<ReasonContributor> active = contributors.stream().filter(c -> c.supports(mode)).toList();
    Map<Integer, List<FightPlayerDomain>> players =
        group(fightPlayerRepo.findByMatchId(matchId), FightPlayerDomain::getFightIndex);
    Map<Integer, List<FightAbilityUseDomain>> uses =
        group(abilityUseRepo.findByMatchId(matchId), FightAbilityUseDomain::getFightIndex);
    Map<Integer, List<DeathAnalysisDomain>> deaths =
        group(deathRepo.findByMatchId(matchId), DeathAnalysisDomain::getFightIndex);
    List<PlayerRoleDomain> roles = roleRepo.findByMatchId(matchId);
    List<TeamCompositionDomain> compositions = compositionRepo.findByMatchId(matchId);
    List<FightReasonDomain> rows = new ArrayList<>();
    for (FightDomain fight : fightRepo.findByMatchId(matchId)) {
      int index = fight.getFightIndex();
      FightContext context =
          new FightContext(
              matchId,
              mode,
              fight,
              players.getOrDefault(index, List.of()),
              uses.getOrDefault(index, List.of()),
              deaths.getOrDefault(index, List.of()),
              roles,
              compositions);
      Map<String, Reason> byKey = new LinkedHashMap<>();
      for (ReasonContributor contributor : active) {
        for (Reason reason : contributor.contribute(context)) {
          byKey.merge(
              reason.scope()
                  + "/"
                  + reason.team()
                  + "/"
                  + reason.subjectSlot()
                  + "/"
                  + reason.code(),
              reason,
              (a, b) -> a.severity() >= b.severity() ? a : b);
        }
      }
      byKey.values().forEach(r -> rows.add(row(matchId, index, r)));
    }
    reasonRepo.saveAll(rows);
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    reasonRepo.deleteByMatchId(matchId);
  }

  private static <T> Map<Integer, List<T>> group(List<T> rows, Function<T, Integer> fightIndex) {
    return rows.stream()
        .filter(r -> fightIndex.apply(r) != null)
        .collect(Collectors.groupingBy(fightIndex));
  }

  private FightReasonDomain row(long matchId, int fightIndex, Reason reason) {
    return FightReasonDomain.builder()
        .matchId(matchId)
        .fightIndex(fightIndex)
        .subjectSlot(reason.subjectSlot())
        .team(reason.team().name())
        .scope(reason.scope().name())
        .code(reason.code())
        .severity(reason.severity())
        .evidence(reason.evidence())
        .source(reason.source())
        .analysisVersion(version())
        .build();
  }
}
