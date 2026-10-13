package com.ako.dbuff.service.replay.runeloop;

import com.ako.dbuff.dao.repo.FightImportanceRepo;
import com.ako.dbuff.dao.repo.FightReasonRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.OpeningSummaryRepo;
import com.ako.dbuff.dao.repo.RunePickupRepo;
import com.ako.dbuff.service.replay.analysis.rune.RuneProperties;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopDiff.Section;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopTopic;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.StoredMatchModes;
import com.ako.dbuff.service.replay.report.ReportHeroNames;
import com.ako.dbuff.service.replay.runeloop.RuneLoopReport.MatchData;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

/** The runes learning loop: the opening, every rune taken and the runes that mattered in fights. */
@Component
class RuneLoopTopic implements ReviewLoopTopic {
  static final Path MATCH_LIST = Path.of("src/test/resources/replay/rune-loop/matches.txt");
  static final List<Section> SECTIONS =
      List.of(
          new Section(RuneLoopReport.OPENING, "", 1),
          new Section(RuneLoopReport.RUNES, "", 1),
          new Section(RuneLoopReport.ADVANTAGE, "", 1));

  private final StoredMatchModes matchModes;
  private final MatchRepo matchRepo;
  private final ReportHeroNames heroNames;
  private final RunePickupRepo pickupRepo;
  private final OpeningSummaryRepo summaryRepo;
  private final FightRepo fightRepo;
  private final FightImportanceRepo importanceRepo;
  private final FightReasonRepo reasonRepo;
  private final RuneProperties properties;

  RuneLoopTopic(
      StoredMatchModes matchModes,
      MatchRepo matchRepo,
      ReportHeroNames heroNames,
      RunePickupRepo pickupRepo,
      OpeningSummaryRepo summaryRepo,
      FightRepo fightRepo,
      FightImportanceRepo importanceRepo,
      FightReasonRepo reasonRepo,
      RuneProperties properties) {
    this.matchModes = matchModes;
    this.matchRepo = matchRepo;
    this.heroNames = heroNames;
    this.pickupRepo = pickupRepo;
    this.summaryRepo = summaryRepo;
    this.fightRepo = fightRepo;
    this.importanceRepo = importanceRepo;
    this.reasonRepo = reasonRepo;
    this.properties = properties;
  }

  @Override
  public String name() {
    return "runes";
  }

  @Override
  public Path matchList() {
    return MATCH_LIST;
  }

  @Override
  public Path outputDir() {
    return Path.of("build", "rune-loop");
  }

  @Override
  public List<Section> sections() {
    return SECTIONS;
  }

  @Override
  public String render(long matchId) {
    String mode =
        matchModes.activeFor(matchId).map(GameModeProfile::name).orElse("mode not active");
    return RuneLoopReport.render(
        new MatchData(
            matchId,
            matchRepo.findById(matchId).orElseThrow(),
            mode,
            heroNames.bySlot(matchId),
            pickupRepo.findByMatchId(matchId),
            summaryRepo.findByMatchId(matchId),
            fightRepo.findByMatchId(matchId),
            importanceRepo.findByMatchId(matchId),
            reasonRepo.findByMatchId(matchId),
            properties));
  }
}
