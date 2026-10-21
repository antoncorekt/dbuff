package com.ako.dbuff.service.replay.skillloop;

import com.ako.dbuff.dao.repo.BuildFitRepo;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.MissedPickRepo;
import com.ako.dbuff.dao.repo.SkillEffectRepo;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopDiff.Section;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopTopic;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.StoredMatchModes;
import com.ako.dbuff.service.replay.report.AbilityLabels;
import com.ako.dbuff.service.replay.report.ReportHeroNames;
import com.ako.dbuff.service.replay.skillloop.SkillLoopReport.MatchData;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The skills learning loop: the missed picks, the build fit verdicts and the skill effects stored
 * for each match.
 */
@Component
class SkillLoopTopic implements ReviewLoopTopic {
  static final Path MATCH_LIST = Path.of("src/test/resources/replay/skill-loop/matches.txt");
  static final List<Section> SECTIONS =
      List.of(
          new Section(SkillLoopReport.MISSED_PICKS, "", 1),
          new Section(SkillLoopReport.BUILD_FIT, "", 1),
          new Section(SkillLoopReport.SKILL_EFFECTS, "", 1));

  private final StoredMatchModes matchModes;
  private final MatchRepo matchRepo;
  private final ReportHeroNames heroNames;
  private final MissedPickRepo missedPickRepo;
  private final BuildFitRepo buildFitRepo;
  private final SkillEffectRepo skillEffectRepo;
  private final AbilityLabels labels;

  SkillLoopTopic(
      StoredMatchModes matchModes,
      MatchRepo matchRepo,
      ReportHeroNames heroNames,
      MissedPickRepo missedPickRepo,
      BuildFitRepo buildFitRepo,
      SkillEffectRepo skillEffectRepo,
      AbilityLabels labels) {
    this.matchModes = matchModes;
    this.matchRepo = matchRepo;
    this.heroNames = heroNames;
    this.missedPickRepo = missedPickRepo;
    this.buildFitRepo = buildFitRepo;
    this.skillEffectRepo = skillEffectRepo;
    this.labels = labels;
  }

  @Override
  public String name() {
    return "skills";
  }

  @Override
  public Path matchList() {
    return MATCH_LIST;
  }

  @Override
  public Path outputDir() {
    return Path.of("build", "skill-loop");
  }

  @Override
  public List<Section> sections() {
    return SECTIONS;
  }

  @Override
  public String render(long matchId) {
    String mode =
        matchModes.activeFor(matchId).map(GameModeProfile::name).orElse("mode not active");
    return SkillLoopReport.render(
        new MatchData(
            matchId,
            matchRepo.findById(matchId).orElseThrow(),
            mode,
            heroNames.bySlot(matchId),
            missedPickRepo.findByMatchId(matchId),
            buildFitRepo.findByMatchId(matchId),
            skillEffectRepo.findByMatchId(matchId),
            labels));
  }
}
