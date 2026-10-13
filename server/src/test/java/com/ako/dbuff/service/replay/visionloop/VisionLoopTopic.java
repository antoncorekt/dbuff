package com.ako.dbuff.service.replay.visionloop;

import com.ako.dbuff.dao.repo.FightVisionRepo;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.WardEventRepo;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopDiff.Section;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopTopic;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.StoredMatchModes;
import com.ako.dbuff.service.replay.report.ReportHeroNames;
import com.ako.dbuff.service.replay.visionloop.VisionLoopReport.MatchData;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The vision learning loop: what each team could see around every important fight, and the verdict.
 */
@Component
class VisionLoopTopic implements ReviewLoopTopic {
  static final Path MATCH_LIST = Path.of("src/test/resources/replay/vision-loop/matches.txt");
  static final List<Section> SECTIONS = List.of(new Section(VisionLoopReport.VISION, "window", 2));

  private final StoredMatchModes matchModes;
  private final MatchRepo matchRepo;
  private final ReportHeroNames heroNames;
  private final FightExplanationReader reader;
  private final FightVisionRepo visionRepo;
  private final WardEventRepo wardRepo;

  VisionLoopTopic(
      StoredMatchModes matchModes,
      MatchRepo matchRepo,
      ReportHeroNames heroNames,
      FightExplanationReader reader,
      FightVisionRepo visionRepo,
      WardEventRepo wardRepo) {
    this.matchModes = matchModes;
    this.matchRepo = matchRepo;
    this.heroNames = heroNames;
    this.reader = reader;
    this.visionRepo = visionRepo;
    this.wardRepo = wardRepo;
  }

  @Override
  public String name() {
    return "vision";
  }

  @Override
  public Path matchList() {
    return MATCH_LIST;
  }

  @Override
  public Path outputDir() {
    return Path.of("build", "vision-loop");
  }

  @Override
  public List<Section> sections() {
    return SECTIONS;
  }

  @Override
  public String render(long matchId) {
    String mode =
        matchModes.activeFor(matchId).map(GameModeProfile::name).orElse("mode not active");
    return VisionLoopReport.render(
        new MatchData(
            matchId,
            matchRepo.findById(matchId).orElseThrow(),
            mode,
            reader.importantFights(matchId),
            visionRepo.findByMatchId(matchId),
            heroNames.bySlot(matchId),
            wardRepo.findByMatchId(matchId)));
  }
}
