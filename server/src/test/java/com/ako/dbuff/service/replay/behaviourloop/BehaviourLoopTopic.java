package com.ako.dbuff.service.replay.behaviourloop;

import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.ObjectiveConversionRepo;
import com.ako.dbuff.dao.repo.PlayerRoleRepo;
import com.ako.dbuff.dao.repo.SmokeUseRepo;
import com.ako.dbuff.dao.repo.TeamLaneSwapRepo;
import com.ako.dbuff.dao.repo.TeamRotationRepo;
import com.ako.dbuff.dao.repo.TeamSplitStretchRepo;
import com.ako.dbuff.service.replay.analysis.behaviour.BehaviourProperties;
import com.ako.dbuff.service.replay.behaviourloop.BehaviourLoopReport.MatchData;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopDiff.Section;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopTopic;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.StoredMatchModes;
import com.ako.dbuff.service.replay.report.ReportHeroNames;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The team behaviour learning loop: rotations of heroes away from their team's group, group or
 * split stretches, lane swaps, smoke uses, objectives after won fights.
 */
@Component
@RequiredArgsConstructor
class BehaviourLoopTopic implements ReviewLoopTopic {
  static final Path MATCH_LIST = Path.of("src/test/resources/replay/behaviour-loop/matches.txt");
  static final List<Section> SECTIONS =
      List.of(
          new Section(BehaviourLoopReport.ROTATIONS, "", 1),
          new Section(BehaviourSplitSections.STRETCHES, "", 1),
          new Section(BehaviourSplitSections.SWAPS, "", 1),
          new Section(BehaviourSmokeSection.SMOKE, "", 1),
          new Section(BehaviourObjectiveSection.OBJECTIVES, "", 1));

  private final StoredMatchModes matchModes;
  private final MatchRepo matchRepo;
  private final ReportHeroNames heroNames;
  private final TeamRotationRepo rotationRepo;
  private final FightRepo fightRepo;
  private final TeamSplitStretchRepo stretchRepo;
  private final TeamLaneSwapRepo swapRepo;
  private final SmokeUseRepo smokeRepo;
  private final ObjectiveConversionRepo objectiveRepo;
  private final PlayerRoleRepo roleRepo;
  private final BehaviourProperties properties;

  @Override
  public String name() {
    return "behaviour";
  }

  @Override
  public Path matchList() {
    return MATCH_LIST;
  }

  @Override
  public Path outputDir() {
    return Path.of("build", "behaviour-loop");
  }

  @Override
  public List<Section> sections() {
    return SECTIONS;
  }

  @Override
  public String render(long matchId) {
    String mode =
        matchModes.activeFor(matchId).map(GameModeProfile::name).orElse("mode not active");
    return BehaviourLoopReport.render(
        new MatchData(
            matchId,
            matchRepo.findById(matchId).orElseThrow(),
            mode,
            heroNames.bySlot(matchId),
            rotationRepo.findByMatchId(matchId),
            fightRepo.findByMatchId(matchId),
            stretchRepo.findByMatchId(matchId),
            swapRepo.findByMatchId(matchId),
            smokeRepo.findByMatchId(matchId),
            objectiveRepo.findByMatchId(matchId),
            roles(matchId),
            properties));
  }

  private Map<Long, Integer> roles(long matchId) {
    return roleRepo.findByMatchId(matchId).stream()
        .filter(r -> r.getPlayerSlot() != null && r.getRole() != null)
        .collect(
            Collectors.toMap(
                PlayerRoleDomain::getPlayerSlot,
                PlayerRoleDomain::getRole,
                (first, second) -> first));
  }
}
