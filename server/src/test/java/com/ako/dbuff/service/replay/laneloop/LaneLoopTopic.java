package com.ako.dbuff.service.replay.laneloop;

import com.ako.dbuff.dao.repo.LaneAssignmentRepo;
import com.ako.dbuff.dao.repo.LanePlayerStatRepo;
import com.ako.dbuff.dao.repo.LaneReasonRepo;
import com.ako.dbuff.dao.repo.LaneResultRepo;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.service.replay.analysis.lane.LaneProperties;
import com.ako.dbuff.service.replay.laneloop.LaneLoopReport.MatchData;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopDiff.Section;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopTopic;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.StoredMatchModes;
import com.ako.dbuff.service.replay.report.ReportHeroNames;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

/** The laning learning loop: who won each lane, the players in it, and why. */
@Component
class LaneLoopTopic implements ReviewLoopTopic {
  static final Path MATCH_LIST = Path.of("src/test/resources/replay/lane-loop/matches.txt");
  static final List<Section> SECTIONS =
      List.of(
          new Section(LaneLoopReport.LANES, "", 1),
          new Section(LaneLoopReport.PLAYERS, "", 1),
          new Section(LaneLoopReport.WHY, "", 1));

  private final StoredMatchModes matchModes;
  private final MatchRepo matchRepo;
  private final ReportHeroNames heroNames;
  private final LaneAssignmentRepo assignmentRepo;
  private final LanePlayerStatRepo statRepo;
  private final LaneResultRepo resultRepo;
  private final LaneReasonRepo reasonRepo;
  private final LaneProperties properties;

  LaneLoopTopic(
      StoredMatchModes matchModes,
      MatchRepo matchRepo,
      ReportHeroNames heroNames,
      LaneAssignmentRepo assignmentRepo,
      LanePlayerStatRepo statRepo,
      LaneResultRepo resultRepo,
      LaneReasonRepo reasonRepo,
      LaneProperties properties) {
    this.matchModes = matchModes;
    this.matchRepo = matchRepo;
    this.heroNames = heroNames;
    this.assignmentRepo = assignmentRepo;
    this.statRepo = statRepo;
    this.resultRepo = resultRepo;
    this.reasonRepo = reasonRepo;
    this.properties = properties;
  }

  @Override
  public String name() {
    return "lanes";
  }

  @Override
  public Path matchList() {
    return MATCH_LIST;
  }

  @Override
  public Path outputDir() {
    return Path.of("build", "lane-loop");
  }

  @Override
  public List<Section> sections() {
    return SECTIONS;
  }

  @Override
  public String render(long matchId) {
    String mode =
        matchModes.activeFor(matchId).map(GameModeProfile::name).orElse("mode not active");
    return LaneLoopReport.render(
        new MatchData(
            matchId,
            matchRepo.findById(matchId).orElseThrow(),
            mode,
            heroNames.bySlot(matchId),
            assignmentRepo.findByMatchId(matchId),
            statRepo.findByMatchId(matchId),
            resultRepo.findByMatchId(matchId),
            reasonRepo.findByMatchId(matchId),
            properties.getUltimateLevel()));
  }
}
