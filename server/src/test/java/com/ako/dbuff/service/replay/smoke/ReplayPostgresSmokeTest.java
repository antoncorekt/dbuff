package com.ako.dbuff.service.replay.smoke;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightExplanationDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerRoleProfileDomain;
import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import com.ako.dbuff.dao.repo.AbilityDraftPoolRepo;
import com.ako.dbuff.dao.repo.BuybackEventRepo;
import com.ako.dbuff.dao.repo.DeathAnalysisRepo;
import com.ako.dbuff.dao.repo.FightExplanationRepo;
import com.ako.dbuff.dao.repo.FightPlayerRepo;
import com.ako.dbuff.dao.repo.FightReasonRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.GameSpikeRepo;
import com.ako.dbuff.dao.repo.GameStateSegmentRepo;
import com.ako.dbuff.dao.repo.PlayerActivitySegmentRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.PlayerPhaseSummaryRepo;
import com.ako.dbuff.dao.repo.PlayerRoleProfileRepo;
import com.ako.dbuff.dao.repo.PlayerRoleRepo;
import com.ako.dbuff.dao.repo.ReplayCombatLogRepo;
import com.ako.dbuff.dao.repo.ReplayParseRunRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerMinuteRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerTrackRepo;
import com.ako.dbuff.dao.repo.ReplayStateChangeRepo;
import com.ako.dbuff.dao.repo.TeamCompositionRepo;
import com.ako.dbuff.service.replay.ReplayDataService;
import com.ako.dbuff.service.replay.ReplayRunService;
import com.ako.dbuff.service.replay.analysis.activity.Activity;
import com.ako.dbuff.service.replay.analysis.composition.CompositionStage;
import com.ako.dbuff.service.replay.analysis.composition.CompositionToken;
import com.ako.dbuff.service.replay.analysis.fight.Presence;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.reason.ReasonScope;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.report.CompositionSection;
import com.ako.dbuff.service.replay.report.DeathSection;
import com.ako.dbuff.service.replay.report.DraftSection;
import com.ako.dbuff.service.replay.report.FightSection;
import com.ako.dbuff.service.replay.report.MovementSection;
import com.ako.dbuff.service.replay.report.ReplayReportInput;
import com.ako.dbuff.service.replay.report.SwingSection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs a real replay through the whole pipeline against a real PostgreSQL: a local HTTP server
 * stands in for Valve, the real downloader, decompressor, parser, extractors and analyzers run, and
 * the tables are created by Hibernate as in production. It needs a PostgreSQL URL in {@code
 * SMOKE_PG_URL} (user postgres, password password) and the replays and OpenDota's JSON of them in
 * {@code REPLAY_FIXTURE_DIR}, as {@code <id>.dem.zst} and {@code <id>.json}. OpenDota's gold stands
 * in for the net worth timeline, so the analyzers that read it run too.
 */
@Tag("replay")
@EnabledIfEnvironmentVariable(named = "SMOKE_PG_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "REPLAY_FIXTURE_DIR", matches = ".+")
@ReplayPipelineTest
class ReplayPostgresSmokeTest extends ReplayPipelineFixture {

  private static final String[] MATCHES = {"9040889566", "9040892342"};
  private static final String[] ABILITY_DRAFT_MATCHES = {"9038725154", "9037038717"};

  @Autowired private AbilityDraftPickRepo draftPickRepo;
  @Autowired private AbilityDraftPoolRepo draftPoolRepo;
  @Autowired private ReplayRunService runService;
  @Autowired private ReplayDataService dataService;
  @Autowired private ReplayParseRunRepo runRepo;
  @Autowired private ReplayPlayerRepo playerRepo;
  @Autowired private ReplayPlayerTrackRepo trackRepo;
  @Autowired private ReplayPlayerMinuteRepo minuteRepo;
  @Autowired private ReplayStateChangeRepo stateRepo;
  @Autowired private ReplayCombatLogRepo combatLogRepo;
  @Autowired private PlayerActivitySegmentRepo activityRepo;
  @Autowired private FightRepo fightRepo;
  @Autowired private DeathAnalysisRepo deathRepo;
  @Autowired private FightReasonRepo reasonRepo;
  @Autowired private FightExplanationRepo explanationRepo;
  @Autowired private FightExplanationReader explanationReader;
  @Autowired private BuybackEventRepo buybackRepo;
  @Autowired private PlayerPhaseSummaryRepo phaseRepo;
  @Autowired private GameSpikeRepo spikeRepo;
  @Autowired private GameStateSegmentRepo stateSegmentRepo;
  @Autowired private PlayerRoleRepo roleRepo;
  @Autowired private PlayerRoleProfileRepo roleProfileRepo;
  @Autowired private TeamCompositionRepo compositionRepo;
  @Autowired private SwingSection swingSection;
  @Autowired private DeathSection deathSection;
  @Autowired private FightSection fightSection;
  @Autowired private MovementSection movementSection;
  @Autowired private DraftSection draftSection;
  @Autowired private CompositionSection compositionSection;
  @Autowired private PlayerGameStatisticRepo playerStatRepo;
  @Autowired private HeroKits heroKits;
  @Autowired private FightPlayerRepo fightPlayerRepo;

  @Test
  @Commit
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void aRealReplayGoesThroughEveryStageAndLandsInPostgres() throws Exception {
    for (String id : MATCHES) {
      MatchDomain match = seedMatch(id);

      ReplayStatus status = runService.process(match);

      ReplayParseRunDomain run =
          runRepo.findByMatchIdAndParserVersion(match.getId(), 1).orElseThrow();
      assertThat(status).as("%s: %s", id, run.getError()).isEqualTo(ReplayStatus.DONE);
      assertThat(playerRepo.findByMatchId(match.getId())).hasSize(10);
      assertThat(draftPickRepo.findByMatchId(match.getId())).as("%s has no draft", id).isEmpty();
      assertThat(draftPoolRepo.findAll().stream().filter(d -> d.getMatchId().equals(match.getId())))
          .isEmpty();
      assertThat(trackRepo.findByMatchId(match.getId())).hasSize(10);
      assertThat(trackRepo.findByMatchId(match.getId()).getFirst().getMana()).isNotEmpty();
      assertThat(minuteRepo.findByMatchId(match.getId())).isNotEmpty();
      assertThat(stateRepo.findByMatchIdOrderByGameSeconds(match.getId())).isNotEmpty();
      assertThat(combatLogRepo.findAll().stream().filter(c -> c.getMatchId().equals(match.getId())))
          .hasSize(1);
      assertThat(activityRepo.findByMatchId(match.getId())).isNotEmpty();
      assertThat(fightRepo.findByMatchId(match.getId())).isNotEmpty();
      assertThat(deathRepo.findByMatchId(match.getId())).hasSizeGreaterThan(40);
      assertThat(reasonRepo.findByMatchId(match.getId())).as("%s: fight reasons", id).isNotEmpty();
      assertThat(phaseRepo.findByMatchId(match.getId())).hasSizeGreaterThanOrEqualTo(20);
      assertThat(roleRepo.findByMatchId(match.getId()))
          .as("%s: each team has each role once", id)
          .hasSize(10)
          .extracting(r -> r.getRole() + (r.getPlayerSlot() >= 128 ? 10 : 0))
          .containsExactlyInAnyOrder(1, 2, 3, 4, 5, 11, 12, 13, 14, 15);
      assertThat(roleProfileRepo.findByMatchId(match.getId()))
          .as("%s: one final profile per player", id)
          .hasSize(10)
          .allSatisfy(p -> assertThat(p.getStage()).isEqualTo("FINAL"));
      assertThat(compositionRepo.findByMatchId(match.getId()))
          .as("%s: one final composition per team", id)
          .hasSize(2)
          .allSatisfy(
              c -> {
                assertThat(c.getStage()).isEqualTo("FINAL");
                assertThat(c.getSummary()).isNotBlank();
              })
          .extracting(c -> c.getTeam())
          .containsExactlyInAnyOrder("RADIANT", "DIRE");
      assertThat(buybackRepo.findByMatchId(match.getId())).isNotEmpty();
      assertThat(spikeRepo.findByMatchId(match.getId())).isNotEmpty();
      assertThat(stateSegmentRepo.findByMatchId(match.getId())).isNotEmpty();
      ReplayReportInput input =
          new ReplayReportInput(
              match.getId(),
              match,
              java.util.Set.of(0L, 1L, 2L, 3L, 4L, 128L, 129L, 130L, 131L, 132L),
              java.util.Map.of(
                  0L, "P0", 1L, "P1", 2L, "P2", 3L, "P3", 4L, "P4", 128L, "P5", 129L, "P6", 130L,
                  "P7", 131L, "P8", 132L, "P9"),
              3);
      java.util.List<String> report = new java.util.ArrayList<>();
      report.addAll(swingSection.lines(input));
      report.addAll(deathSection.lines(input));
      report.addAll(fightSection.lines(input));
      report.addAll(movementSection.lines(input));
      report.addAll(draftSection.lines(input));
      report.addAll(compositionSection.lines(input));
      System.out.println("REPORT " + id + "\n" + String.join("\n", report));
      assertThat(report).isNotEmpty();
    }
    ReplayStatus again = runService.process(seedMatch(MATCHES[0]));
    assertThat(again).as("a second run replaces the first").isEqualTo(ReplayStatus.DONE);
    assertThat(deathRepo.findByMatchId(Long.parseLong(MATCHES[0]))).hasSizeGreaterThan(40);
    dataService.deleteByMatchId(Long.parseLong(MATCHES[0]));
    assertThat(deathRepo.findByMatchId(Long.parseLong(MATCHES[0]))).isEmpty();
    assertThat(reasonRepo.findByMatchId(Long.parseLong(MATCHES[0]))).isEmpty();
    assertThat(explanationRepo.findByMatchId(Long.parseLong(MATCHES[0]))).isEmpty();
  }

  @Test
  @Commit
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void anAbilityDraftReplayGetsIntentAndFinalRolesAndAPickAndAFinalVerdict() throws Exception {
    for (String id : ABILITY_DRAFT_MATCHES) {
      Assumptions.assumeTrue(hasFixture(id), id + " is not in the fixture directory");
      MatchDomain match = seedMatch(id);

      ReplayStatus status = runService.process(match);

      ReplayParseRunDomain run =
          runRepo.findByMatchIdAndParserVersion(match.getId(), 1).orElseThrow();
      assertThat(status).as("%s: %s", id, run.getError()).isEqualTo(ReplayStatus.DONE);
      assertThat(draftPickRepo.findByMatchId(match.getId())).as("%s draft", id).hasSize(40);
      assertThat(roleRepo.findByMatchId(match.getId()))
          .as("%s: each team has each role once", id)
          .hasSize(10)
          .extracting(r -> r.getRole() + (r.getPlayerSlot() >= 128 ? 10 : 0))
          .containsExactlyInAnyOrder(1, 2, 3, 4, 5, 11, 12, 13, 14, 15);
      List<PlayerRoleProfileDomain> profiles = roleProfileRepo.findByMatchId(match.getId());
      assertThat(profiles).as("%s: intent and final profiles", id).hasSize(20);
      assertThat(reasonRepo.findByMatchId(match.getId())).as("%s: fight reasons", id).isNotEmpty();
      assertThat(compositionRepo.findByMatchId(match.getId()))
          .as("%s: a pick and a final verdict per team", id)
          .extracting(c -> c.getTeam() + " " + c.getStage())
          .containsExactlyInAnyOrder("RADIANT PICK", "RADIANT FINAL", "DIRE PICK", "DIRE FINAL");
      assertRoleAwareReasons(id, match.getId());
      assertFightExplanations(id, match.getId());
      printAbilityDraft(id, match.getId(), profiles);
    }
  }

  private void assertFightExplanations(String id, long matchId) {
    List<FightDomain> decided =
        fightRepo.findByMatchId(matchId).stream()
            .filter(f -> "RADIANT".equals(f.getWinner()) || "DIRE".equals(f.getWinner()))
            .toList();
    List<FightExplanationDomain> explanations = explanationRepo.findByMatchId(matchId);
    assertThat(explanations)
        .as("%s: one explanation per fight with a winner", id)
        .extracting(FightExplanationDomain::getFightIndex)
        .containsExactlyInAnyOrderElementsOf(
            decided.stream().map(FightDomain::getFightIndex).toList());
    Map<Integer, FightDomain> fights = new TreeMap<>();
    decided.forEach(f -> fights.put(f.getFightIndex(), f));
    List<FightReasonDomain> allReasons =
        reasonRepo.findByMatchId(matchId).stream().filter(r -> r.getFightIndex() != null).toList();
    assertThat(explanations)
        .allSatisfy(
            e -> {
              String winner = fights.get(e.getFightIndex()).getWinner();
              assertThat(e.getLosingTeam()).isNotEqualTo(winner);
              long ofLoser =
                  allReasons.stream()
                      .filter(
                          r ->
                              r.getFightIndex().equals(e.getFightIndex())
                                  && e.getLosingTeam().equals(r.getTeam()))
                      .count();
              assertThat(e.getReasonCount())
                  .as("%s fight %d", id, e.getFightIndex())
                  .isEqualTo((int) ofLoser);
            });
    FightExplanationReader.MatchExplanation read = explanationReader.read(matchId, 3, 2);
    assertThat(read.swings()).hasSameSizeAs(spikeRepo.findByMatchId(matchId));
    assertThat(read.swings()).allSatisfy(s -> assertThat(s.fights()).hasSizeLessThanOrEqualTo(2));
    List<String> swingLines =
        swingSection.lines(new ReplayReportInput(matchId, null, Set.of(), Map.of(), 3));
    System.out.println("SWINGS " + id + "\n" + String.join("\n", swingLines));
    assertThat(String.join("\n", swingLines))
        .as("%s: swings fit a message", id)
        .hasSizeLessThan(2000);
    System.out.println(
        "FIGHT LOSS "
            + id
            + ": "
            + explanations.size()
            + " lost fights, "
            + explanations.stream().filter(e -> e.getReasonCount() == 0).count()
            + " without a reason");
  }

  /** Derives the expected rows from the stored roles, fight players and compositions. */
  private void assertRoleAwareReasons(String id, long matchId) {
    Map<Long, Integer> roles = new TreeMap<>();
    roleRepo.findByMatchId(matchId).forEach(r -> roles.put(r.getPlayerSlot(), r.getRole()));
    Set<String> expected = new TreeSet<>();
    for (FightPlayerDomain p : fightPlayerRepo.findByMatchId(matchId)) {
      Presence presence = Enums.find(Presence.class, p.getPresence());
      Activity activity = Enums.find(Activity.class, p.getActivityAtStart());
      Integer role = roles.get(p.getPlayerSlot());
      if (presence == null || !presence.isAbsentAlive() || role == null) {
        continue;
      }
      if (role >= 4) {
        expected.add(p.getFightIndex() + "/" + p.getPlayerSlot() + "/SUPPORT_ABSENT");
      }
      if (role <= 2 && (activity == Activity.JUNGLE_FARM || activity == Activity.LANE_FARM)) {
        expected.add(p.getFightIndex() + "/" + p.getPlayerSlot() + "/CARRY_FARMING_ELSEWHERE");
      }
    }
    List<FightReasonDomain> stored = reasonRepo.findByMatchId(matchId);
    Set<String> storedPlayer =
        stored.stream()
            .filter(
                r ->
                    r.getCode().equals("SUPPORT_ABSENT")
                        || r.getCode().equals("CARRY_FARMING_ELSEWHERE"))
            .map(r -> r.getFightIndex() + "/" + r.getSubjectSlot() + "/" + r.getCode())
            .collect(Collectors.toCollection(TreeSet::new));
    assertThat(storedPlayer)
        .as("%s: role-aware player reasons follow roles, presence and activity", id)
        .isEqualTo(expected);

    Map<String, String> finalTokens = new TreeMap<>();
    compositionRepo.findByMatchId(matchId).stream()
        .filter(c -> c.getStage().equals(CompositionStage.FINAL.name()))
        .forEach(c -> finalTokens.put(c.getTeam(), c.getTokens()));
    Set<String> expectedGaps = new TreeSet<>();
    for (FightDomain fight : fightRepo.findByMatchId(matchId)) {
      TeamSide winner = Enums.find(TeamSide.class, fight.getWinner());
      if (winner == null) {
        continue;
      }
      String loser = (winner == TeamSide.RADIANT ? TeamSide.DIRE : TeamSide.RADIANT).name();
      for (String token : finalTokens.getOrDefault(loser, "").split(",")) {
        Arrays.stream(CompositionToken.values())
            .filter(known -> known.name().equals(token.trim()) && known.function() != null)
            .forEach(known -> expectedGaps.add(fight.getFightIndex() + "/" + loser + "/" + known));
      }
    }
    Set<String> storedGaps =
        stored.stream()
            .filter(r -> r.getScope().equals(ReasonScope.TEAM.name()))
            .map(r -> r.getFightIndex() + "/" + r.getTeam() + "/" + r.getCode())
            .collect(Collectors.toCollection(TreeSet::new));
    assertThat(storedGaps)
        .as("%s: team gaps are the losing team's final function tokens", id)
        .isEqualTo(expectedGaps);
  }

  private void printAbilityDraft(String id, long matchId, List<PlayerRoleProfileDomain> profiles) {
    Map<Long, List<String>> held = new TreeMap<>();
    draftPickRepo.findByMatchId(matchId).stream()
        .sorted(Comparator.comparing(p -> p.getPickOrder()))
        .forEach(
            p ->
                held.computeIfAbsent(p.getPlayerSlot(), s -> new ArrayList<>())
                    .add(p.getAbilityName()));
    StringBuilder out = new StringBuilder("AD ROLES " + id + "\n");
    List<PlayerMatchStatisticDomain> stats =
        playerStatRepo.findAllByMatchId(matchId).stream()
            .sorted(Comparator.comparing(PlayerMatchStatisticDomain::getPlayerSlot))
            .toList();
    for (PlayerMatchStatisticDomain stat : stats) {
      long slot = stat.getPlayerSlot();
      PlayerRoleProfileDomain intent = stageRow(profiles, slot, "INTENT");
      PlayerRoleProfileDomain fin = stageRow(profiles, slot, "FINAL");
      out.append(
          String.format(
              "  slot %3d %-18s nw %6d lane_role %d intent %s (%.2f) final %s (%.2f)%n"
                  + "      skills %s%n",
              slot,
              heroKits
                  .hero(stat.getHeroId())
                  .map(h -> h.getLocalized_name())
                  .orElse("hero " + stat.getHeroId()),
              stat.getNetWorth(),
              stat.getLaneRole(),
              intent == null ? "-" : intent.getRole(),
              intent == null ? 0.0 : intent.getConfidence(),
              fin == null ? "-" : fin.getRole(),
              fin == null ? 0.0 : fin.getConfidence(),
              held.getOrDefault(slot, List.of())));
    }
    compositionRepo.findByMatchId(matchId).stream()
        .sorted(Comparator.comparing(c -> c.getTeam() + c.getStage()))
        .forEach(
            c ->
                out.append(
                    String.format(
                        "  %s %s: [%s] %s%n",
                        c.getTeam(), c.getStage(), c.getTokens(), c.getSummary())));
    System.out.println(out);
  }

  private static PlayerRoleProfileDomain stageRow(
      List<PlayerRoleProfileDomain> profiles, long slot, String stage) {
    return profiles.stream()
        .filter(p -> p.getPlayerSlot() == slot && p.getStage().equals(stage))
        .findFirst()
        .orElse(null);
  }
}
