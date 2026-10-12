package com.ako.dbuff.service.replay.roleloop;

import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.PlayerRoleProfileRepo;
import com.ako.dbuff.dao.repo.PlayerRoleRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.dao.repo.TeamCompositionRepo;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopDiff.Section;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopTopic;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.StoredMatchModes;
import com.ako.dbuff.service.replay.roleloop.RoleLoopReport.MatchData;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

/** The role learning loop: the stored roles, verdicts and compositions of each listed match. */
@Component
class RoleLoopTopic implements ReviewLoopTopic {
  static final Path MATCH_LIST = Path.of("src/test/resources/replay/role-loop/matches.txt");
  static final List<Section> SECTIONS =
      List.of(
          new Section(RoleLoopReport.ROLES, "slot", 1),
          new Section(RoleLoopReport.VERDICTS, "", 2));

  private final StoredMatchModes matchModes;
  private final MatchRepo matchRepo;
  private final PlayerGameStatisticRepo statRepo;
  private final ReplayPlayerRepo replayPlayerRepo;
  private final PlayerRoleRepo roleRepo;
  private final PlayerRoleProfileRepo profileRepo;
  private final TeamCompositionRepo compositionRepo;
  private final AbilityDraftPickRepo pickRepo;

  RoleLoopTopic(
      StoredMatchModes matchModes,
      MatchRepo matchRepo,
      PlayerGameStatisticRepo statRepo,
      ReplayPlayerRepo replayPlayerRepo,
      PlayerRoleRepo roleRepo,
      PlayerRoleProfileRepo profileRepo,
      TeamCompositionRepo compositionRepo,
      AbilityDraftPickRepo pickRepo) {
    this.matchModes = matchModes;
    this.matchRepo = matchRepo;
    this.statRepo = statRepo;
    this.replayPlayerRepo = replayPlayerRepo;
    this.roleRepo = roleRepo;
    this.profileRepo = profileRepo;
    this.compositionRepo = compositionRepo;
    this.pickRepo = pickRepo;
  }

  @Override
  public String name() {
    return "roles";
  }

  @Override
  public Path matchList() {
    return MATCH_LIST;
  }

  @Override
  public Path outputDir() {
    return Path.of("build", "role-loop");
  }

  @Override
  public List<Section> sections() {
    return SECTIONS;
  }

  @Override
  public String render(long matchId) {
    String mode =
        matchModes.activeFor(matchId).map(GameModeProfile::name).orElse("mode not active");
    return RoleLoopReport.render(
        new MatchData(
            matchId,
            matchRepo.findById(matchId).orElseThrow(),
            mode,
            statRepo.findAllByMatchId(matchId),
            replayPlayerRepo.findByMatchId(matchId),
            roleRepo.findByMatchId(matchId),
            profileRepo.findByMatchId(matchId),
            compositionRepo.findByMatchId(matchId),
            pickRepo.findByMatchId(matchId)));
  }
}
