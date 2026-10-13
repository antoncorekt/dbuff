package com.ako.dbuff.service.replay.smoke;

import com.ako.dbuff.dao.model.CreepKillDomain;
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
import com.ako.dbuff.dao.repo.CreepKillRepo;
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
import com.ako.dbuff.service.replay.analysis.creep.CreepClass;
import com.ako.dbuff.service.replay.analysis.fight.Presence;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.reason.Reason;
import com.ako.dbuff.service.replay.analysis.reason.ReasonScope;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import com.ako.dbuff.service.replay.analysis.role.IntentProfileCalculator;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.report.CompositionSection;
import com.ako.dbuff.service.replay.report.DeathSection;
import com.ako.dbuff.service.replay.report.DraftSection;
import com.ako.dbuff.service.replay.report.FightSection;
import com.ako.dbuff.service.replay.report.MovementSection;
import com.ako.dbuff.service.replay.report.ReplayReportInput;
import com.ako.dbuff.service.replay.report.SwingSection;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
  @Autowired private CreepKillRepo creepKillRepo;
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
  @Autowired private IntentProfileCalculator intentCalculator;

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
      assertAllPickReasons(id, match.getId());
      assertCreepKills(id, match.getId());
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
    assertThat(creepKillRepo.findByMatchId(Long.parseLong(MATCHES[0]))).isEmpty();
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
      assertCreepKills(id, match.getId());
      assertAbilityDraftReasons(id, match.getId());
      assertFightExplanations(id, match.getId());
      printAbilityDraft(id, match.getId(), profiles);
    }
  }

  /** Per hero, the last hits and denies the stored creep kills give against OpenDota's totals. */
  private void assertCreepKills(String id, long matchId) throws Exception {
    List<CreepKillDomain> kills = creepKillRepo.findByMatchId(matchId);
    assertThat(kills).as("%s: creep kills", id).isNotEmpty();
    assertThat(kills)
        .extracting(CreepKillDomain::getCreepClass)
        .as("%s: every class is a known one", id)
        .allSatisfy(c -> assertThat(CreepClass.valueOf(c)).isNotNull());
    long linked = kills.stream().filter(k -> !k.getDenied() && k.getGold() != null).count();
    long paid = kills.stream().filter(k -> !k.getDenied()).count();
    assertThat(linked).as("%s: kills with their gold", id).isGreaterThan(paid * 8 / 10);
    JsonNode players = new ObjectMapper().readTree(fixture(id + ".json").toFile()).get("players");
    long ours = 0;
    long theirs = 0;
    for (JsonNode p : players) {
      long slot = p.get("player_slot").asLong();
      List<CreepKillDomain> own = kills.stream().filter(k -> k.getPlayerSlot() == slot).toList();
      long lastHits = own.stream().filter(k -> !k.getDenied()).count();
      long denies = own.stream().filter(CreepKillDomain::getDenied).count();
      int openDotaLastHits = p.get("last_hits").asInt();
      System.out.printf(
          "CREEP KILLS %s slot %d: last hits %d (OpenDota %d), denies %d (OpenDota %d), lane %d,"
              + " camp %d, siege %d%n",
          id,
          slot,
          lastHits,
          openDotaLastHits,
          denies,
          p.get("denies").asInt(),
          own.stream().filter(k -> k.getCreepClass().startsWith("LANE_") && !k.getDenied()).count(),
          own.stream().filter(k -> k.getCreepClass().startsWith("NEUTRAL")).count(),
          own.stream().filter(k -> k.getCreepClass().startsWith("LANE_SIEGE")).count());
      assertThat(lastHits)
          .as(
              "%s slot %d: the log has no kill by a hero's summon, never more than OpenDota",
              id, slot)
          .isLessThanOrEqualTo(openDotaLastHits + 3L);
      assertThat(denies)
          .as("%s slot %d denies", id, slot)
          .isLessThanOrEqualTo(p.get("denies").asInt() + 2L);
      ours += lastHits;
      theirs += openDotaLastHits;
    }
    assertThat(ours)
        .as("%s: last hits of all heroes against OpenDota's %d", id, theirs)
        .isGreaterThanOrEqualTo(Math.round(theirs * 0.75));
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
            .filter(r -> r.getSource().equals(Reason.COMMON))
            .map(r -> r.getFightIndex() + "/" + r.getTeam() + "/" + r.getCode())
            .collect(Collectors.toCollection(TreeSet::new));
    assertThat(storedGaps)
        .as("%s: team gaps are the losing team's final function tokens", id)
        .isEqualTo(expectedGaps);
  }

  private static Set<String> keys(List<FightReasonDomain> rows, String code) {
    return rows.stream()
        .filter(r -> r.getCode().equals(code))
        .map(r -> r.getFightIndex() + "/" + r.getTeam() + "/" + r.getSubjectSlot())
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private static String countsPerCode(List<FightReasonDomain> rows, String... codes) {
    return Arrays.stream(codes)
        .map(c -> c + "=" + keys(rows, c).size())
        .collect(Collectors.joining(" "));
  }

  private static TeamSide loserOf(FightDomain fight) {
    TeamSide winner = Enums.find(TeamSide.class, fight.getWinner());
    if (winner == null) {
      return null;
    }
    return winner == TeamSide.RADIANT ? TeamSide.DIRE : TeamSide.RADIANT;
  }

  private static boolean inFight(FightPlayerDomain p) {
    Presence presence = Enums.find(Presence.class, p.getPresence());
    return presence == Presence.ACTIVE || presence == Presence.PRESENT_PASSIVE;
  }

  /** Derives the expected Ability Draft rows from the stored profiles, compositions and pool. */
  private void assertAbilityDraftReasons(String id, long matchId) {
    List<FightReasonDomain> stored = reasonRepo.findByMatchId(matchId);
    Map<Long, Map<String, Integer>> roles = new TreeMap<>();
    roleProfileRepo
        .findByMatchId(matchId)
        .forEach(
            p ->
                roles
                    .computeIfAbsent(p.getPlayerSlot(), s -> new TreeMap<>())
                    .put(p.getStage(), p.getRole()));
    Set<String> expectedIntent = new TreeSet<>();
    for (FightPlayerDomain p : fightPlayerRepo.findByMatchId(matchId)) {
      Map<String, Integer> byStage = roles.getOrDefault(p.getPlayerSlot(), Map.of());
      Integer intent = byStage.get("INTENT");
      Integer played = byStage.get("FINAL");
      if (inFight(p) && intent != null && played != null && !intent.equals(played)) {
        expectedIntent.add(
            p.getFightIndex() + "/" + TeamSide.ofSlot(p.getPlayerSlot()) + "/" + p.getPlayerSlot());
      }
    }
    assertThat(keys(stored, "INTENT_NOT_PLAYED"))
        .as("%s: heroes in the fight whose intent role differs from the final one", id)
        .isEqualTo(expectedIntent);

    Map<TeamSide, Map<String, Double>> peaks = new TreeMap<>();
    Map<TeamSide, Long> intentProfiled = new TreeMap<>();
    for (PlayerRoleProfileDomain p : roleProfileRepo.findByMatchId(matchId)) {
      if (p.getStage().equals("INTENT")) {
        intentProfiled.merge(TeamSide.ofSlot(p.getPlayerSlot()), 1L, Long::sum);
        Map<String, Double> team =
            peaks.computeIfAbsent(TeamSide.ofSlot(p.getPlayerSlot()), t -> new TreeMap<>());
        team.merge("initiation", p.getInitiation() == null ? -1 : p.getInitiation(), Math::max);
        team.merge("disable", p.getDisable() == null ? -1 : p.getDisable(), Math::max);
        team.merge("damage", p.getDamage() == null ? -1 : p.getDamage(), Math::max);
      }
    }
    Set<String> skippedStrong =
        draftPoolRepo.findByMatchId(matchId).stream()
            .filter(p -> p.getPickedBySlot() == null && p.getAbilityName() != null)
            .filter(p -> isStrong(p.getAbilityName()))
            .map(p -> p.getAbilityName())
            .collect(Collectors.toCollection(TreeSet::new));
    Set<String> expectedWeak = new TreeSet<>();
    Set<String> expectedStrong = new TreeSet<>();
    for (FightDomain fight : fightRepo.findByMatchId(matchId)) {
      TeamSide loser = loserOf(fight);
      if (loser == null) {
        continue;
      }
      String key = fight.getFightIndex() + "/" + loser + "/null";
      long profiled = intentProfiled.getOrDefault(loser, 0L);
      long gaps =
          profiled < 4
              ? 0
              : peaks.getOrDefault(loser, Map.of()).values().stream()
                  .filter(v -> v >= 0 && v < 0.6)
                  .count();
      if (gaps >= 2) {
        expectedWeak.add(key);
      }
    }
    assertThat(keys(stored, "WEAK_COMBINATION"))
        .as(
            "%s: the losing team's best player stays below 0.6 in two of initiation, disable,"
                + " damage",
            id)
        .isEqualTo(expectedWeak);
    assertThat(keys(stored, "STRONG_SKILL_LEFT"))
        .as("%s: STRONG_SKILL_LEFT is off by default", id)
        .isEqualTo(expectedStrong);
    assertThat(stored)
        .filteredOn(r -> r.getCode().equals("HERO_ROLE_MISMATCH"))
        .as("%s: no All Pick reason in an Ability Draft", id)
        .isEmpty();
    System.out.println(
        "AD REASONS "
            + id
            + " fights="
            + fightRepo.findByMatchId(matchId).size()
            + " "
            + countsPerCode(stored, "INTENT_NOT_PLAYED", "WEAK_COMBINATION", "STRONG_SKILL_LEFT")
            + " strongSkippedSkills="
            + skippedStrong
            + " intentPeaks="
            + peaks
            + " skipped="
            + skippedValues(matchId));
  }

  private String skippedValues(long matchId) {
    return draftPoolRepo.findByMatchId(matchId).stream()
        .filter(p -> p.getPickedBySlot() == null && p.getAbilityName() != null)
        .map(
            p ->
                heroKits
                    .ability(p.getAbilityName())
                    .map(
                        c -> {
                          var f =
                              intentCalculator.profile(
                                  null,
                                  List.of(
                                      new IntentProfileCalculator.HeldAbility(
                                          p.getAbilityName(), c, false)));
                          return String.format(
                              "%s(i%.2f d%.2f x%.2f)",
                              p.getAbilityName(), f.initiation(), f.disable(), f.damage());
                        })
                    .orElse(p.getAbilityName() + "(no constant)"))
        .collect(Collectors.joining(" "));
  }

  private boolean isStrong(String abilityName) {
    var constant = heroKits.ability(abilityName);
    if (constant.isEmpty()) {
      return false;
    }
    var profile =
        intentCalculator.profile(
            null,
            List.of(new IntentProfileCalculator.HeldAbility(abilityName, constant.get(), false)));
    return Stream.of(profile.initiation(), profile.disable(), profile.damage())
        .anyMatch(v -> v != null && v >= 0.35);
  }

  /** Derives the expected All Pick rows from the stored roles, heroes and fight presence. */
  private void assertAllPickReasons(String id, long matchId) {
    List<FightReasonDomain> stored = reasonRepo.findByMatchId(matchId);
    Map<Long, Integer> roles = new TreeMap<>();
    roleRepo.findByMatchId(matchId).forEach(r -> roles.put(r.getPlayerSlot(), r.getRole()));
    Map<Long, Long> heroes = new TreeMap<>();
    playerStatRepo
        .findAllByMatchId(matchId)
        .forEach(s -> heroes.put(s.getPlayerSlot(), s.getHeroId()));
    Map<Integer, Set<String>> fitting =
        Map.of(
            1, Set.of("Carry"),
            2, Set.of("Carry", "Nuker"),
            3, Set.of("Durable", "Initiator"),
            4, Set.of("Support", "Initiator", "Disabler"),
            5, Set.of("Support"));
    Set<String> expected = new TreeSet<>();
    for (FightPlayerDomain p : fightPlayerRepo.findByMatchId(matchId)) {
      Integer role = roles.get(p.getPlayerSlot());
      List<String> tags =
          heroes.get(p.getPlayerSlot()) == null
              ? List.of()
              : heroKits
                  .hero(heroes.get(p.getPlayerSlot()))
                  .map(h -> h.getRoles())
                  .orElse(List.of());
      if (inFight(p)
          && role != null
          && !tags.isEmpty()
          && tags.stream().noneMatch(fitting.get(role)::contains)) {
        expected.add(
            p.getFightIndex() + "/" + TeamSide.ofSlot(p.getPlayerSlot()) + "/" + p.getPlayerSlot());
      }
    }
    assertThat(keys(stored, "HERO_ROLE_MISMATCH"))
        .as("%s: heroes in the fight whose tags do not fit the final role", id)
        .isEqualTo(expected);
    assertThat(stored)
        .extracting(FightReasonDomain::getCode)
        .as("%s: no Ability Draft reason in an All Pick", id)
        .doesNotContain("INTENT_NOT_PLAYED", "WEAK_COMBINATION", "STRONG_SKILL_LEFT");
    System.out.println(
        "AP REASONS "
            + id
            + " fights="
            + fightRepo.findByMatchId(matchId).size()
            + " "
            + countsPerCode(stored, "HERO_ROLE_MISMATCH")
            + " "
            + stored.stream()
                .filter(r -> r.getCode().equals("HERO_ROLE_MISMATCH"))
                .collect(
                    Collectors.groupingBy(
                        r -> r.getEvidence() + " sev " + r.getSeverity(),
                        TreeMap::new,
                        Collectors.counting())));
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
