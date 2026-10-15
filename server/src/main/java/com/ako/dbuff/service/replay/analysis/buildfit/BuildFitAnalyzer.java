package com.ako.dbuff.service.replay.analysis.buildfit;

import com.ako.dbuff.dao.model.BuildFitDomain;
import com.ako.dbuff.dao.model.DeathAnalysisDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerRoleProfileDomain;
import com.ako.dbuff.dao.repo.BuildFitRepo;
import com.ako.dbuff.dao.repo.DeathAnalysisRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.PlayerRoleProfileRepo;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.role.FunctionProfile;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import com.ako.dbuff.service.replay.analysis.role.NetWorthRanks;
import com.ako.dbuff.service.replay.analysis.role.RoleFunction;
import com.ako.dbuff.service.replay.analysis.role.RoleStage;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Judges whether the skills each Ability Draft player drafted fit the role he was meant to play
 * (INTENT) and the role he played (FINAL). The build is the INTENT profile, the only stage that
 * knows the skills; the roles, the deaths alone and the net worth place come from stored tables.
 * One verdict is written when both roles are the same, two when they differ.
 */
@Slf4j
@Order(98)
@Component
@RequiredArgsConstructor
public class BuildFitAnalyzer implements ReplayAnalyzer {
  private static final int TEAM_SIZE = 5;

  private final PlayerRoleProfileRepo profileRepo;
  private final PlayerGameStatisticRepo statisticRepo;
  private final DeathAnalysisRepo deathRepo;
  private final BuildFitRepo buildFitRepo;
  private final BuildFitJudge judge;
  private final HeroKits heroKits;

  @Override
  public String name() {
    return "build-fit";
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  public boolean supports(GameModeProfile mode) {
    return mode.gameModeId() == AbilityDraftProfile.MODE_ID;
  }

  @Override
  public Set<String> requires() {
    return Set.of("roles");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    Map<Long, PlayerRoleProfileDomain> intent = bySlot(matchId, RoleStage.INTENT);
    if (intent.isEmpty()) {
      log.info("Match {}: no intent profiles stored, no build fit", matchId);
      return;
    }
    Map<Long, PlayerRoleProfileDomain> played = bySlot(matchId, RoleStage.FINAL);
    List<PlayerMatchStatisticDomain> stats = statisticRepo.findAllByMatchId(matchId);
    Map<Long, Double> places = netWorthPlaces(stats);
    Map<Long, Long> heroes = new HashMap<>();
    stats.forEach(s -> heroes.put(s.getPlayerSlot(), s.getHeroId()));
    Map<Long, Integer> lonelyDeaths =
        deathRepo.findByMatchId(matchId).stream()
            .filter(d -> Boolean.TRUE.equals(d.getIsolated()))
            .collect(
                Collectors.toMap(
                    DeathAnalysisDomain::getPlayerSlot, d -> 1, Integer::sum, HashMap::new));
    List<BuildFitDomain> rows = new ArrayList<>();
    for (PlayerRoleProfileDomain drafted : intent.values()) {
      long slot = drafted.getPlayerSlot();
      FunctionProfile build = FunctionProfile.stored(drafted);
      if (build.of(RoleFunction.ESCAPE) == null) {
        log.info(
            "Match {}: slot {} has no escape value stored, no build fit for him", matchId, slot);
        continue;
      }
      BuildFitJudge.Evidence evidence =
          new BuildFitJudge.Evidence(
              lonelyDeaths.getOrDefault(slot, 0), places.getOrDefault(slot, Double.NaN));
      String hero = heroName(heroes.get(slot));
      PlayerRoleProfileDomain actual = played.get(slot);
      if (actual == null || actual.getRole().equals(drafted.getRole())) {
        rows.add(row(matchId, slot, BuildFitStage.BOTH, drafted.getRole(), hero, build, evidence));
      } else {
        rows.add(
            row(matchId, slot, BuildFitStage.INTENT, drafted.getRole(), hero, build, evidence));
        rows.add(row(matchId, slot, BuildFitStage.FINAL, actual.getRole(), hero, build, evidence));
      }
    }
    buildFitRepo.saveAll(rows);
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    buildFitRepo.deleteByMatchId(matchId);
  }

  private BuildFitDomain row(
      long matchId,
      long slot,
      BuildFitStage stage,
      int role,
      String hero,
      FunctionProfile build,
      BuildFitJudge.Evidence evidence) {
    BuildFitJudge.Verdict verdict = judge.judge(build, role, evidence);
    return BuildFitDomain.builder()
        .matchId(matchId)
        .playerSlot(slot)
        .stage(stage.name())
        .role(role)
        .missing(names(verdict.missing().stream().map(Enum::name).toList()))
        .stated(names(verdict.gaps().stream().map(g -> g.function().name()).toList()))
        .evidence(
            cut(
                BuildFitWording.verdict(hero, stage, role, verdict.gaps()),
                BuildFitDomain.EVIDENCE_LENGTH))
        .analysisVersion(version())
        .build();
  }

  private Map<Long, PlayerRoleProfileDomain> bySlot(long matchId, RoleStage stage) {
    Map<Long, PlayerRoleProfileDomain> bySlot = new HashMap<>();
    for (PlayerRoleProfileDomain p : profileRepo.findByMatchId(matchId)) {
      if (stage.name().equals(p.getStage()) && p.getPlayerSlot() != null && p.getRole() != null) {
        bySlot.put(p.getPlayerSlot(), p);
      }
    }
    return bySlot;
  }

  private static Map<Long, Double> netWorthPlaces(List<PlayerMatchStatisticDomain> stats) {
    Map<Long, Double> places = new HashMap<>();
    for (TeamSide side : TeamSide.values()) {
      List<PlayerMatchStatisticDomain> team =
          stats.stream()
              .filter(s -> s.getPlayerSlot() != null && TeamSide.ofSlot(s.getPlayerSlot()) == side)
              .toList();
      if (team.size() != TEAM_SIZE) {
        continue;
      }
      double[] ranks =
          NetWorthRanks.of(
              team.stream()
                  .map(s -> s.getNetWorth() == null ? null : (double) s.getNetWorth())
                  .toList());
      for (int i = 0; i < TEAM_SIZE; i++) {
        places.put(team.get(i).getPlayerSlot(), ranks[i]);
      }
    }
    return places;
  }

  private String heroName(Long heroId) {
    return heroId == null
        ? "The hero"
        : heroKits.hero(heroId).map(HeroConstant::getLocalized_name).orElse("Hero " + heroId);
  }

  private static String names(List<String> functions) {
    return cut(String.join(",", functions), BuildFitDomain.FUNCTIONS_LENGTH);
  }

  private static String cut(String text, int max) {
    return text.length() <= max ? text : text.substring(0, max - 3) + "...";
  }
}
