package com.ako.dbuff.service.replay.fightloop;

import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.TeamfightPlayerRepo;
import com.ako.dbuff.dao.repo.TeamfightRepo;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader;
import com.ako.dbuff.service.replay.fightloop.FightLoopReport.MatchData;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopDiff.Section;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopTopic;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.StoredMatchModes;
import com.ako.dbuff.service.replay.report.ReportHeroNames;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

/** The fights learning loop: the lost fights of each swing and the reasons stored for them. */
@Component
class FightLoopTopic implements ReviewLoopTopic {
  static final Path MATCH_LIST = Path.of("src/test/resources/replay/fight-loop/matches.txt");
  static final List<Section> SECTIONS =
      List.of(
          new Section(FightLoopReport.FIGHTS, "", 1), new Section(FightLoopReport.REASONS, "", 2));

  private final StoredMatchModes matchModes;
  private final MatchRepo matchRepo;
  private final ReportHeroNames heroNames;
  private final TeamfightRepo teamfightRepo;
  private final TeamfightPlayerRepo teamfightPlayerRepo;
  private final FightExplanationReader reader;

  FightLoopTopic(
      StoredMatchModes matchModes,
      MatchRepo matchRepo,
      ReportHeroNames heroNames,
      TeamfightRepo teamfightRepo,
      TeamfightPlayerRepo teamfightPlayerRepo,
      FightExplanationReader reader) {
    this.matchModes = matchModes;
    this.matchRepo = matchRepo;
    this.heroNames = heroNames;
    this.teamfightRepo = teamfightRepo;
    this.teamfightPlayerRepo = teamfightPlayerRepo;
    this.reader = reader;
  }

  @Override
  public String name() {
    return "fights";
  }

  @Override
  public Path matchList() {
    return MATCH_LIST;
  }

  @Override
  public Path outputDir() {
    return Path.of("build", "fight-loop");
  }

  @Override
  public List<Section> sections() {
    return SECTIONS;
  }

  @Override
  public String render(long matchId) {
    String mode =
        matchModes.activeFor(matchId).map(GameModeProfile::name).orElse("mode not active");
    return FightLoopReport.render(
        new MatchData(
            matchId,
            matchRepo.findById(matchId).orElseThrow(),
            mode,
            reader.read(matchId, Integer.MAX_VALUE, Integer.MAX_VALUE),
            heroNames.bySlot(matchId),
            teamfightRepo.findByMatchId(matchId),
            teamfightPlayerRepo.findByMatchId(matchId)));
  }
}
