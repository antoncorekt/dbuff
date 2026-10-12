package com.ako.dbuff.service.replay.roleloop;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.PlayerRoleProfileRepo;
import com.ako.dbuff.dao.repo.PlayerRoleRepo;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.dao.repo.TeamCompositionRepo;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.ReplayRunService;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisService;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.StoredMatchModes;
import com.ako.dbuff.service.replay.roleloop.RoleLoopReport.MatchData;
import com.ako.dbuff.service.replay.smoke.ReplayPipelineFixture;
import com.ako.dbuff.service.replay.smoke.ReplayPipelineTest;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * One round of the role learning loop: stores or re-analyses every match of {@code matches.txt}
 * against the local PostgreSQL and writes the review reports (see docs/replay-role-loop). Run it
 * with {@code :server:roleLoop}.
 */
@Tag("roleloop")
@EnabledIfEnvironmentVariable(named = "REPLAY_FIXTURE_DIR", matches = ".+")
@ReplayPipelineTest
class RoleLoopTest extends ReplayPipelineFixture {
  private static final Path OUTPUT = Path.of("build", "role-loop");
  private static final Path MATCH_LIST = Path.of("src/test/resources/replay/role-loop/matches.txt");

  @Autowired private ReplayRunService runService;
  @Autowired private ReplayAnalysisService analysis;
  @Autowired private StoredMatchModes matchModes;
  @Autowired private ReplayProcessingProperties properties;
  @Autowired private ReplayParseRunRepo runRepo;
  @Autowired private MatchRepo matchRepo;
  @Autowired private PlayerGameStatisticRepo statRepo;
  @Autowired private ReplayPlayerRepo replayPlayerRepo;
  @Autowired private PlayerRoleRepo roleRepo;
  @Autowired private PlayerRoleProfileRepo profileRepo;
  @Autowired private TeamCompositionRepo compositionRepo;
  @Autowired private AbilityDraftPickRepo pickRepo;

  @Test
  @Commit
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void writesTheReviewReportsOfOneRound() {
    RoleLoopRound round =
        RoleLoopRound.open(
            OUTPUT, System.getenv("ROUND"), "1".equals(System.getenv("ROUND_OVERWRITE")));
    List<String> summary = new ArrayList<>();
    summary.add("# Round %02d".formatted(round.number()));
    try {
      for (long id : RoleLoopMatchList.read(MATCH_LIST)) {
        summarise(round, id, summary);
      }
    } finally {
      round.write("summary.md", String.join("\n", summary) + "\n");
      System.out.println(String.join("\n", summary));
      System.out.println("Reports in " + round.dir().toAbsolutePath());
    }
  }

  private record Outcome(String how, String report) {}

  private void summarise(RoleLoopRound round, long id, List<String> summary) {
    Outcome outcome = run(id);
    round.write(id + ".md", outcome.report());
    List<String> changes =
        round
            .previous(id + ".md", r -> !RoleLoopReport.isUnavailable(r))
            .map(before -> RoleLoopDiff.changes(before, outcome.report()))
            .orElse(null);
    summary.add("");
    summary.add("## " + outcome.report().lines().findFirst().orElse("Match " + id).substring(2));
    summary.add("- " + outcome.how());
    summary.addAll(changeLines(changes));
  }

  /** One bad match is reported as such and does not stop the round. */
  private Outcome run(long id) {
    try {
      String how = prepare(id);
      return new Outcome(how, reportOf(id, how));
    } catch (Exception e) {
      String how = "failed: " + e;
      return new Outcome(how, RoleLoopReport.unavailable(id, how));
    }
  }

  private static List<String> changeLines(List<String> changes) {
    if (changes == null) {
      return List.of("- no earlier round to compare with");
    }
    if (changes.isEmpty()) {
      return List.of("- unchanged since the earlier round");
    }
    return changes.stream().map(c -> "- " + c).toList();
  }

  private String reportOf(long id, String how) {
    Optional<MatchDomain> match = matchRepo.findById(id);
    if (match.isEmpty() || how.startsWith("skipped")) {
      return RoleLoopReport.unavailable(id, how);
    }
    String mode = matchModes.activeFor(id).map(GameModeProfile::name).orElse("mode not active");
    return RoleLoopReport.render(
        new MatchData(
            id,
            match.get(),
            mode,
            statRepo.findAllByMatchId(id),
            replayPlayerRepo.findByMatchId(id),
            roleRepo.findByMatchId(id),
            profileRepo.findByMatchId(id),
            compositionRepo.findByMatchId(id),
            pickRepo.findByMatchId(id)));
  }

  /** The stored data is the source: the replay is read only once, for a match not stored yet. */
  private String prepare(long id) throws IOException {
    Optional<ReplayParseRunDomain> run =
        runRepo.findByMatchIdAndParserVersion(id, properties.getParserVersion());
    if (run.isPresent() && run.get().getStatus() == ReplayStatus.DONE) {
      Optional<GameModeProfile> mode = matchModes.activeFor(id);
      if (mode.isEmpty()) {
        return "skipped: the game mode of the stored match is not active for replay processing";
      }
      analysis.analyze(id, mode.get(), List.of());
      return "re-analysed from the stored data";
    }
    if (!hasFixture(String.valueOf(id))) {
      return "skipped: not stored and no fixture "
          + id
          + ".json and "
          + id
          + ".dem.zst in REPLAY_FIXTURE_DIR";
    }
    ReplayStatus status = runService.process(seedMatch(String.valueOf(id)));
    return status == ReplayStatus.DONE
        ? "stored from the replay"
        : "skipped: replay run ended " + status;
  }
}
