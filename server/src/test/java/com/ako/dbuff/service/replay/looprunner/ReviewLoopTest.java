package com.ako.dbuff.service.replay.looprunner;

import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import com.ako.dbuff.service.replay.ReplayRunService;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisService;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.StoredMatchModes;
import com.ako.dbuff.service.replay.smoke.ReplayPipelineFixture;
import com.ako.dbuff.service.replay.smoke.ReplayPipelineTest;
import java.io.IOException;
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
 * One round of a review loop: stores or re-analyses every match of the topic's list against the
 * local PostgreSQL and writes the review reports (see docs/replay-review-loop.md). Run it with
 * {@code :server:reviewLoop -Ploop.topic=<name>}.
 */
@Tag("reviewloop")
@EnabledIfEnvironmentVariable(named = "REPLAY_FIXTURE_DIR", matches = ".+")
@ReplayPipelineTest
class ReviewLoopTest extends ReplayPipelineFixture {
  private static final String DEFAULT_TOPIC = "roles";

  @Autowired private ReplayRunService runService;
  @Autowired private ReplayAnalysisService analysis;
  @Autowired private StoredMatchModes matchModes;
  @Autowired private List<ReviewLoopTopic> topics;
  @Autowired private ReplayProcessingProperties properties;
  @Autowired private ReplayParseRunRepo runRepo;
  @Autowired private MatchRepo matchRepo;

  @Test
  @Commit
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void writesTheReviewReportsOfOneRound() {
    ReviewLoopTopic topic = selectedTopic();
    List<Long> matches = ReviewLoopMatchList.read(topic.matchList());
    ReviewLoopRound round =
        ReviewLoopRound.open(
            topic.outputDir(),
            System.getenv("ROUND"),
            "1".equals(System.getenv("ROUND_OVERWRITE")));
    List<String> summary = new ArrayList<>();
    summary.add("# Round %02d".formatted(round.number()));
    try {
      for (long id : matches) {
        summarise(topic, round, id, summary);
      }
    } finally {
      round.write("summary.md", String.join("\n", summary) + "\n");
      System.out.println(String.join("\n", summary));
      System.out.println("Reports in " + round.dir().toAbsolutePath());
    }
  }

  private record Outcome(String how, String report) {}

  private void summarise(
      ReviewLoopTopic topic, ReviewLoopRound round, long id, List<String> summary) {
    Outcome outcome = run(topic, id);
    round.write(id + ".md", outcome.report());
    Optional<String> before = round.previous(id + ".md", r -> !ReviewLoopReport.isUnavailable(r));
    summary.add("");
    summary.add("## " + outcome.report().lines().findFirst().orElse("Match " + id).substring(2));
    summary.add("- " + outcome.how());
    summary.addAll(ReviewLoopDiff.summaryLines(before, outcome.report(), topic.sections()));
  }

  /** One bad match is reported as such and does not stop the round. */
  private Outcome run(ReviewLoopTopic topic, long id) {
    try {
      String how = prepare(id);
      return new Outcome(how, reportOf(topic, id, how));
    } catch (Exception e) {
      String how = "failed: " + e;
      return new Outcome(how, ReviewLoopReport.unavailable(id, how));
    }
  }

  private String reportOf(ReviewLoopTopic topic, long id, String how) {
    if (how.startsWith("skipped") || !matchRepo.existsById(id)) {
      return ReviewLoopReport.unavailable(id, how);
    }
    return topic.render(id);
  }

  private ReviewLoopTopic selectedTopic() {
    String name = Optional.ofNullable(System.getenv("LOOP_TOPIC")).orElse(DEFAULT_TOPIC);
    return topics.stream()
        .filter(t -> t.name().equals(name))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "unknown loop.topic '%s', known: %s"
                        .formatted(name, topics.stream().map(ReviewLoopTopic::name).toList())));
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
