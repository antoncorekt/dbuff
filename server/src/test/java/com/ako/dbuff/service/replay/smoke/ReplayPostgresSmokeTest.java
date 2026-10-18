package com.ako.dbuff.service.replay.smoke;

import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.model.CreepKillDomain;
import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightExplanationDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.dao.model.HeroDraftPickDomain;
import com.ako.dbuff.dao.model.LaneAssignmentDomain;
import com.ako.dbuff.dao.model.LaneResultDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MissedPickDomain;
import com.ako.dbuff.dao.model.OpeningSummaryDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerRoleProfileDomain;
import com.ako.dbuff.dao.model.ReplayParseRunDomain;
import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.dao.model.ReplayStatus;
import com.ako.dbuff.dao.model.RunePickupDomain;
import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import com.ako.dbuff.dao.repo.AbilityDraftPoolRepo;
import com.ako.dbuff.dao.repo.BuildFitRepo;
import com.ako.dbuff.dao.repo.BuybackEventRepo;
import com.ako.dbuff.dao.repo.CreepKillRepo;
import com.ako.dbuff.dao.repo.DeathAnalysisRepo;
import com.ako.dbuff.dao.repo.EndGameSummaryRepo;
import com.ako.dbuff.dao.repo.FightExplanationRepo;
import com.ako.dbuff.dao.repo.FightPlayerRepo;
import com.ako.dbuff.dao.repo.FightReasonRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.GameSpikeRepo;
import com.ako.dbuff.dao.repo.GameStateSegmentRepo;
import com.ako.dbuff.dao.repo.HeroDraftPickRepo;
import com.ako.dbuff.dao.repo.ItemEfficiencyRepo;
import com.ako.dbuff.dao.repo.LaneAssignmentRepo;
import com.ako.dbuff.dao.repo.LaneReasonRepo;
import com.ako.dbuff.dao.repo.LaneResultRepo;
import com.ako.dbuff.dao.repo.MidActivityRepo;
import com.ako.dbuff.dao.repo.MissedPickRepo;
import com.ako.dbuff.dao.repo.OpeningSummaryRepo;
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
import com.ako.dbuff.dao.repo.RunePickupRepo;
import com.ako.dbuff.dao.repo.TeamCompositionRepo;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.CombatLogCodec;
import com.ako.dbuff.service.replay.ReplayDataService;
import com.ako.dbuff.service.replay.ReplayRunService;
import com.ako.dbuff.service.replay.StateChangeKind;
import com.ako.dbuff.service.replay.analysis.activity.Activity;
import com.ako.dbuff.service.replay.analysis.composition.CompositionStage;
import com.ako.dbuff.service.replay.analysis.composition.CompositionToken;
import com.ako.dbuff.service.replay.analysis.creep.CreepClass;
import com.ako.dbuff.service.replay.analysis.fight.Presence;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiencyJson;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import com.ako.dbuff.service.replay.analysis.reason.Reason;
import com.ako.dbuff.service.replay.analysis.reason.ReasonScope;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import com.ako.dbuff.service.replay.analysis.role.IntentProfileCalculator;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.analysis.rune.RuneType;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.read.AnalysisAssembler;
import com.ako.dbuff.service.replay.report.CompositionSection;
import com.ako.dbuff.service.replay.report.DeathSection;
import com.ako.dbuff.service.replay.report.DraftBoardSection;
import com.ako.dbuff.service.replay.report.DraftSection;
import com.ako.dbuff.service.replay.report.EndGameSection;
import com.ako.dbuff.service.replay.report.FarmChartBuilder;
import com.ako.dbuff.service.replay.report.FightSection;
import com.ako.dbuff.service.replay.report.GraphAdvantageSection;
import com.ako.dbuff.service.replay.report.GraphFarmSection;
import com.ako.dbuff.service.replay.report.GraphIncomeSection;
import com.ako.dbuff.service.replay.report.ImportantFightsSection;
import com.ako.dbuff.service.replay.report.ItemsSection;
import com.ako.dbuff.service.replay.report.LanesSection;
import com.ako.dbuff.service.replay.report.MidActivitySection;
import com.ako.dbuff.service.replay.report.MissedPickWording;
import com.ako.dbuff.service.replay.report.MovementSection;
import com.ako.dbuff.service.replay.report.ObjectivesSection;
import com.ako.dbuff.service.replay.report.OpeningSection;
import com.ako.dbuff.service.replay.report.OutliersSection;
import com.ako.dbuff.service.replay.report.ReplayReportInput;
import com.ako.dbuff.service.replay.report.RotationsSection;
import com.ako.dbuff.service.replay.report.SkillPicksSection;
import com.ako.dbuff.service.replay.report.SmokeSection;
import com.ako.dbuff.service.replay.report.SwingSection;
import com.ako.dbuff.service.replay.report.VisionSection;
import com.ako.dbuff.service.replay.report.chart.ChartRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
import static org.assertj.core.api.Assertions.within;

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
  private static final String GRAPH_MATCH = "9044252857";
  private static final String[] INCOME_MATCHES = {GRAPH_MATCH, "9044256304"};
  private static final String[] ABILITY_DRAFT_MATCHES = {"9038725154", "9037038717"};

  @Autowired private AbilityDraftPickRepo draftPickRepo;
  @Autowired private MissedPickRepo missedPickRepo;
  @Autowired private AbilityDraftPoolRepo draftPoolRepo;
  @Autowired private HeroDraftPickRepo heroDraftRepo;
  @Autowired private ReplayRunService runService;
  @Autowired private MeterRegistry meterRegistry;
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
  @Autowired private RunePickupRepo runePickupRepo;
  @Autowired private ItemEfficiencyRepo itemEfficiencyRepo;
  @Autowired private LaneAssignmentRepo laneAssignmentRepo;
  @Autowired private LaneResultRepo laneResultRepo;
  @Autowired private LaneReasonRepo laneReasonRepo;
  @Autowired private OpeningSummaryRepo openingSummaryRepo;
  @Autowired private DeathAnalysisRepo deathRepo;
  @Autowired private FightReasonRepo reasonRepo;
  @Autowired private FightExplanationRepo explanationRepo;
  @Autowired private FightExplanationReader explanationReader;
  @Autowired private AnalysisAssembler analysisAssembler;
  @Autowired private BuybackEventRepo buybackRepo;
  @Autowired private PlayerPhaseSummaryRepo phaseRepo;
  @Autowired private GameSpikeRepo spikeRepo;
  @Autowired private com.ako.dbuff.dao.repo.GameSpikeFactorRepo spikeFactorRepo;
  @Autowired private com.ako.dbuff.service.replay.report.ReportHeroNames reportHeroNames;
  @Autowired private com.ako.dbuff.service.replay.report.AbilityLabels abilityLabels;
  @Autowired private GameStateSegmentRepo stateSegmentRepo;
  @Autowired private PlayerRoleRepo roleRepo;
  @Autowired private PlayerRoleProfileRepo roleProfileRepo;
  @Autowired private TeamCompositionRepo compositionRepo;
  @Autowired private SwingSection swingSection;
  @Autowired private ImportantFightsSection importantFightsSection;
  @Autowired private LanesSection lanesSection;
  @Autowired private RotationsSection rotationsSection;
  @Autowired private SmokeSection smokeSection;
  @Autowired private ObjectivesSection objectivesSection;
  @Autowired private EndGameSection endGameSection;
  @Autowired private EndGameSummaryRepo endGameRepo;
  @Autowired private MidActivitySection midActivitySection;
  @Autowired private SkillPicksSection skillPicksSection;
  @Autowired private MidActivityRepo midActivityRepo;
  @Autowired private ItemsSection itemsSection;
  @Autowired private OutliersSection outliersSection;
  @Autowired private OpeningSection openingSection;
  @Autowired private VisionSection visionSection;
  @Autowired private DeathSection deathSection;
  @Autowired private FightSection fightSection;
  @Autowired private MovementSection movementSection;
  @Autowired private DraftSection draftSection;
  @Autowired private DraftBoardSection draftBoardSection;
  @Autowired private GraphAdvantageSection graphAdvantageSection;
  @Autowired private GraphFarmSection graphFarmSection;
  @Autowired private FarmChartBuilder farmChartBuilder;
  @Autowired private ChartRenderer chartRenderer;
  @Autowired private GraphIncomeSection graphIncomeSection;
  @Autowired private CompositionSection compositionSection;
  @Autowired private PlayerGameStatisticRepo playerStatRepo;
  @Autowired private HeroKits heroKits;
  @Autowired private FightPlayerRepo fightPlayerRepo;
  @Autowired private IntentProfileCalculator intentCalculator;
  @Autowired private BuildFitRepo buildFitRepo;

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
      assertThat(missedPickRepo.findByMatchId(match.getId()))
          .as("%s: missed picks are for Ability Draft only", id)
          .isEmpty();
      assertThat(draftPoolRepo.findAll().stream().filter(d -> d.getMatchId().equals(match.getId())))
          .isEmpty();
      assertThat(heroDraftRepo.findByMatchId(match.getId()))
          .as("%s has no hero draft", id)
          .isEmpty();
      assertThat(trackRepo.findByMatchId(match.getId())).hasSize(10);
      assertThat(trackRepo.findByMatchId(match.getId()).getFirst().getMana()).isNotEmpty();
      assertThat(trackRepo.findByMatchId(match.getId()))
          .allSatisfy(
              t -> {
                assertThat(t.getGold()).isNotNull().isNotEmpty();
                assertThat(Arrays.stream(t.getGold()).min().orElse(-1)).isNotNegative();
                assertThat(t.getMaxHp()).isNotNull().isNotEmpty();
                assertThat(Arrays.stream(t.getMaxHp()).max().orElse(0)).isGreaterThanOrEqualTo(200);
              });
      assertThat(minuteRepo.findByMatchId(match.getId())).isNotEmpty();
      assertThat(stateRepo.findByMatchIdOrderByGameSeconds(match.getId())).isNotEmpty();
      assertThat(combatLogRepo.findAll().stream().filter(c -> c.getMatchId().equals(match.getId())))
          .hasSize(1);
      assertThat(
              combatLogRepo.findAll().stream()
                  .filter(c -> c.getMatchId().equals(match.getId()))
                  .flatMap(c -> CombatLogCodec.decode(c.getData()).stream())
                  .filter(e -> e.kind() == CombatEventKind.HEAL))
          .as("%s: heal events", id)
          .isNotEmpty();
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
      assertThat(buildFitRepo.findByMatchId(match.getId()))
          .as("%s: build fit is Ability Draft only", id)
          .isEmpty();
      buildFitReport().write(id, match.getId(), "All Pick");
      assertCreepKills(id, match.getId());
      assertRunes(id, match.getId());
      assertItemEfficiency(id, match.getId());
      assertLanes(id, match.getId());
      saveMidActivity(id, match);
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
    assertReplayMeters();
    dataService.deleteByMatchId(Long.parseLong(MATCHES[0]));
    assertThat(deathRepo.findByMatchId(Long.parseLong(MATCHES[0]))).isEmpty();
    assertThat(reasonRepo.findByMatchId(Long.parseLong(MATCHES[0]))).isEmpty();
    assertThat(creepKillRepo.findByMatchId(Long.parseLong(MATCHES[0]))).isEmpty();
    assertThat(runePickupRepo.findByMatchId(Long.parseLong(MATCHES[0]))).isEmpty();
    assertThat(openingSummaryRepo.findByMatchId(Long.parseLong(MATCHES[0]))).isEmpty();
    assertThat(itemEfficiencyRepo.findByMatchId(Long.parseLong(MATCHES[0]))).isEmpty();
    assertThat(explanationRepo.findByMatchId(Long.parseLong(MATCHES[0]))).isEmpty();
  }

  @Test
  @Commit
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void anAbilityDraftReplayGetsIntentAndFinalRolesAndAPickAndAFinalVerdict() throws Exception {
    List<String> missedPickDocument = new ArrayList<>(List.of("# Missed picks", ""));
    for (String id : ABILITY_DRAFT_MATCHES) {
      Assumptions.assumeTrue(hasFixture(id), id + " is not in the fixture directory");
      MatchDomain match = seedMatch(id);

      ReplayStatus status = runService.process(match);

      ReplayParseRunDomain run =
          runRepo.findByMatchIdAndParserVersion(match.getId(), 1).orElseThrow();
      assertThat(status).as("%s: %s", id, run.getError()).isEqualTo(ReplayStatus.DONE);
      assertThat(draftPickRepo.findByMatchId(match.getId())).as("%s draft", id).hasSize(40);
      assertHeroDraft(id, match.getId());
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
      assertThat(buildFitRepo.findByMatchId(match.getId()))
          .as("%s: a build verdict for each player, two when the roles differ", id)
          .hasSizeBetween(10, 20);
      buildFitReport().write(id, match.getId(), "Ability Draft");
      assertCreepKills(id, match.getId());
      assertRunes(id, match.getId());
      assertItemEfficiency(id, match.getId());
      assertLanes(id, match.getId());
      saveMidActivity(id, match);
      saveSkillPicks(id, match);
      assertAbilityDraftReasons(id, match.getId());
      assertFightExplanations(id, match.getId());
      saveSwingKeyItems(id, match.getId());
      assertAnalysisDocument(id, match.getId());
      missedPickDocument.addAll(renderMissedPicks(id, match.getId()));
      printAbilityDraft(id, match.getId(), profiles);
      System.out.println(
          "DRAFT LINES "
              + id
              + "\n"
              + String.join(
                  "\n",
                  draftSection.lines(
                      new ReplayReportInput(
                          match.getId(),
                          match,
                          java.util.Set.of(0L),
                          java.util.Map.of(0L, "P0"),
                          3))));
      System.out.println(
          "DRAFT RAW "
              + id
              + " "
              + draftPickRepo.findByMatchId(match.getId()).stream()
                  .filter(d -> Long.valueOf(0).equals(d.getPlayerSlot()))
                  .map(d -> d.getAbilityName())
                  .toList());
    }
    Path dir = Path.of(System.getProperty("user.home"), "dbuff-fixtures", "send");
    Files.createDirectories(dir);
    Files.writeString(dir.resolve("missed-pick.md"), String.join("\n", missedPickDocument) + "\n");
  }

  private static final int MISSED_PICK_LINES_PER_PLAYER = 3;

  /** Damage gaps by ratio, then gaps against a skill with no damage, then fight-only pairs. */
  private static final Comparator<MissedPickDomain> MISSED_PICK_STRENGTH =
      Comparator.comparing(
              (MissedPickDomain r) ->
                  "FIGHTS".equals(r.getBasis()) ? 2 : r.getRatio() == null ? 1 : 0)
          .thenComparing(r -> r.getRatio() == null ? 0.0 : -r.getRatio())
          .thenComparing(r -> -r.getCandidateDamage());

  private String missedLine(MissedPickDomain r, Map<Long, String> heroes) {
    return "- %s: %s (%s did %s, %s's %s did %s)"
        .formatted(
            heroes.getOrDefault(r.getPlayerSlot(), "slot " + r.getPlayerSlot()),
            MissedPickWording.line(r, abilityLabels),
            abilityLabels.label(r.getTakenAbility()),
            MissedPickWording.damage(r.getTakenDamage()),
            heroes.getOrDefault(r.getCandidateOwnerSlot(), "slot " + r.getCandidateOwnerSlot()),
            abilityLabels.label(r.getCandidateAbility()),
            MissedPickWording.damage(r.getCandidateDamage()));
  }

  /**
   * The strongest missed picks of each player of a match in words, with the wording the report will
   * use.
   */
  private List<String> renderMissedPicks(String id, long matchId) {
    Map<Long, String> heroes = reportHeroNames.bySlot(matchId);
    List<MissedPickDomain> rows = missedPickRepo.findByMatchId(matchId);
    List<String> out = new ArrayList<>();
    out.add("## Match " + id);
    out.add("");
    out.add(
        rows.size()
            + " flagged pairs (a skill a player took, a later enemy skill of the same slot kind"
            + " that did more than 10% more total damage and was cast in an important fight its"
            + " team won; for skills with no damage on both sides, more won fights). Three per"
            + " player are shown, damage gaps first. The candidate's own 12-month average is left"
            + " out: it is not stored yet.");
    out.add("");
    Map<Long, List<MissedPickDomain>> byPlayer =
        rows.stream()
            .sorted(MISSED_PICK_STRENGTH)
            .collect(
                Collectors.groupingBy(
                    MissedPickDomain::getPlayerSlot, TreeMap::new, Collectors.toList()));
    byPlayer.forEach(
        (slot, list) -> {
          list.stream()
              .limit(MISSED_PICK_LINES_PER_PLAYER)
              .forEach(r -> out.add(missedLine(r, heroes)));
          if (list.size() > MISSED_PICK_LINES_PER_PLAYER) {
            out.add(
                "  - and %d more for %s"
                    .formatted(
                        list.size() - MISSED_PICK_LINES_PER_PLAYER,
                        heroes.getOrDefault(slot, "slot " + slot)));
          }
        });
    out.add("");
    return out;
  }

  @Test
  @Commit
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void anAllPickDraftBoardListsOpenDotasPicksAndBansInOrder() throws Exception {
    List<String> out = new ArrayList<>();
    for (String id : MATCHES) {
      Assumptions.assumeTrue(hasFixture(id), id + " fixture missing");
      MatchDomain match = seedMatch(id);
      assertThat(match.getGameModeId()).as("%s is an All Pick fixture", id).isEqualTo(22L);
      ReplayReportInput input =
          new ReplayReportInput(match.getId(), match, Set.of(0L), Map.of(0L, "P0"), 3);

      List<String> lines = draftBoardSection.lines(input);

      assertThat(lines).as("%s: board", id).isNotEmpty();
      assertThat(lines.stream().filter(l -> l.matches(".*(: |, )hero .*")))
          .as("%s: picks", id)
          .hasSizeGreaterThanOrEqualTo(8);
      assertThat(lines).contains("**Picks in draft order**");
      out.add("## " + id);
      out.addAll(lines);
    }
    Path dir = Path.of(System.getProperty("user.home"), "dbuff-fixtures", "send");
    Files.createDirectories(dir);
    Files.writeString(dir.resolve("draft-board-allpick.md"), String.join("\n", out) + "\n");
  }

  @Test
  @Commit
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void theAdvantageGraphOfARealMatchIsDrawnFromWhatWasStored() throws Exception {
    Assumptions.assumeTrue(hasFixture(GRAPH_MATCH), GRAPH_MATCH + " fixture missing");
    MatchDomain match = seedMatch(GRAPH_MATCH);
    assertThat(runService.process(match)).isEqualTo(ReplayStatus.DONE);
    ReplayReportInput input =
        new ReplayReportInput(match.getId(), match, Set.of(0L, 1L, 2L, 3L, 4L), Map.of(), 4);

    SectionView view = graphAdvantageSection.view(input).orElseThrow();

    assertThat(view.image()).as("the graph was drawn").isNotNull();
    System.out.println("GRAPH ADVANTAGE " + String.join(" | ", graphAdvantageSection.lines(input)));
    Path dir = Path.of(System.getProperty("user.home"), "dbuff-fixtures", "send");
    Files.createDirectories(dir);
    Files.write(dir.resolve("graph-advantage.png"), view.image().bytes());
  }

  @Test
  @Commit
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void theFarmGraphOfARealMatchIsDrawnFromWhatWasStored() throws Exception {
    Assumptions.assumeTrue(hasFixture(GRAPH_MATCH), GRAPH_MATCH + " fixture missing");
    MatchDomain match = seedMatch(GRAPH_MATCH);
    assertThat(runService.process(match)).isEqualTo(ReplayStatus.DONE);
    Path dir = Path.of(System.getProperty("user.home"), "dbuff-fixtures", "send");
    Files.createDirectories(dir);
    ReplayReportInput input =
        new ReplayReportInput(match.getId(), match, Set.of(0L, 1L, 2L, 3L, 4L), Map.of(), 4);

    SectionView view = graphFarmSection.view(input).orElseThrow();

    assertThat(view.image()).as("the graph was drawn").isNotNull();
    System.out.println("GRAPH FARM " + String.join(" | ", graphFarmSection.lines(input)));
    Files.write(dir.resolve("graph-farm-radiant.png"), view.image().bytes());
    for (long[] slots : new long[][] {{128, 129}, {130}, {131, 132}, {2}}) {
      Set<Long> focus =
          java.util.Arrays.stream(slots).boxed().collect(java.util.stream.Collectors.toSet());
      var chart = farmChartBuilder.build(match.getId(), focus, 2).orElseThrow();
      System.out.println("GRAPH FARM " + focus + " " + chart.verdict() + " " + chart.lines());
      if (chart.spec() != null) {
        Files.write(
            dir.resolve("graph-farm-" + slots[0] + ".png"), chartRenderer.render(chart.spec()));
      }
    }
  }

  @Test
  @Commit
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void theIncomeGraphsOfRealMatchesAreDrawnFromWhatWasStored() throws Exception {
    Path dir = Path.of(System.getProperty("user.home"), "dbuff-fixtures", "send");
    Files.createDirectories(dir);
    for (String id : INCOME_MATCHES) {
      Assumptions.assumeTrue(hasFixture(id), id + " fixture missing");
      MatchDomain match = seedMatch(id);
      assertThat(runService.process(match)).isEqualTo(ReplayStatus.DONE);
      ReplayReportInput input =
          new ReplayReportInput(match.getId(), match, Set.of(0L, 1L, 2L, 3L, 4L), Map.of(), 4);

      SectionView view = graphIncomeSection.view(input).orElseThrow();

      assertThat(view.image()).as("%s: the graph was drawn", id).isNotNull();
      System.out.println(
          "GRAPH INCOME " + id + " " + String.join(" | ", graphIncomeSection.lines(input)));
      Files.write(dir.resolve("graph-income-" + id + ".png"), view.image().bytes());
    }
  }

  private void assertReplayMeters() {
    Timer timer = meterRegistry.get("dbuff.replay.run.duration").tag("status", "DONE").timer();
    double done = meterRegistry.get("dbuff.replay.runs").tag("status", "DONE").gauge().value();
    assertThat(timer.count()).isEqualTo(MATCHES.length + 1);
    assertThat(done).isEqualTo(MATCHES.length);
  }

  private void saveSwingKeyItems(String id, long matchId) {
    Map<Long, String> heroes = reportHeroNames.bySlot(matchId);
    var factors = spikeFactorRepo.findByMatchId(matchId);
    List<String> rows = new java.util.ArrayList<>();
    for (var spike :
        spikeRepo.findByMatchId(matchId).stream()
            .sorted(java.util.Comparator.comparingInt(s -> s.getSpikeIndex()))
            .toList()) {
      String when =
          com.ako.dbuff.service.replay.report.GameTime.clock(spike.getStartSeconds())
              + " to "
              + com.ako.dbuff.service.replay.report.GameTime.clock(spike.getEndSeconds());
      String head =
          "- "
              + when
              + ", "
              + spike.getBenefitingTeam().toLowerCase()
              + " gained "
              + String.format(java.util.Locale.ROOT, "%.1fk", spike.getDeltaGold() / 1000.0)
              + ": ";
      var item =
          factors.stream()
              .filter(
                  f ->
                      "ITEM".equals(f.getFactorType())
                          && spike.getSpikeIndex().equals(f.getSpikeIndex()))
              .toList();
      assertThat(item)
          .as("%s swing %s: at most one item row", id, spike.getSpikeIndex())
          .hasSizeLessThanOrEqualTo(1);
      if (item.isEmpty()) {
        rows.add(head + "no key item");
        continue;
      }
      var f = item.getFirst();
      assertThat(f.getGoldValue()).isGreaterThanOrEqualTo(2000);
      assertThat(f.getSharePercent()).isNull();
      assertThat(Double.parseDouble(f.getDetail())).isLessThanOrEqualTo(spike.getStartSeconds());
      rows.add(
          head
              + heroes.getOrDefault(f.getPlayerSlot(), "slot " + f.getPlayerSlot())
              + " finished "
              + abilityLabels.label("item_" + f.getRef())
              + " ("
              + String.format(java.util.Locale.ROOT, "%.1fk", f.getGoldValue() / 1000.0)
              + ") at "
              + com.ako.dbuff.service.replay.report.GameTime.clock(
                  Double.parseDouble(f.getDetail())));
    }
    String swing =
        String.join(
            "\n", swingSection.lines(new ReplayReportInput(matchId, null, Set.of(), Map.of(), 10)));
    String text =
        "# Key item behind each swing, match "
            + id
            + "\n\n"
            + String.join("\n", rows)
            + "\n\n## Swing section text\n\n"
            + swing
            + "\n";
    System.out.println("SWING KEY ITEMS " + id + "\n" + text);
    try {
      Path dir = Path.of(System.getProperty("user.home"), "dbuff-fixtures", "send");
      Files.createDirectories(dir);
      Files.writeString(dir.resolve("swing-key-items-" + id + ".md"), text);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void assertAnalysisDocument(String id, long matchId) throws Exception {
    Map<String, Object> document = analysisAssembler.assemble(matchId, null);
    assertThat(document.keySet())
        .as("%s: every section", id)
        .containsExactly(
            "run",
            "roles",
            "fights",
            "vision",
            "runes",
            "lanes",
            "items",
            "outliers",
            "behaviour",
            "endgame");
    JsonNode json = new ObjectMapper().valueToTree(document);
    assertThat(json.at("/run/status").asText()).isEqualTo("DONE");
    assertThat(json.at("/roles/players")).as("%s roles", id).hasSize(10);
    assertThat(json.at("/lanes/assignments")).as("%s lane assignments", id).isNotEmpty();
    assertThat(json.at("/lanes/results")).as("%s lane results", id).isNotEmpty();
    assertThat(json.at("/behaviour").findParents("team")).as("%s behaviour rows", id).isNotEmpty();
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

  /**
   * Every player has an assignment, every lane has a window inside the cap and a result for each
   * side with opposite margins, and every side's reasons are ranked from 1.
   */
  private void assertLanes(String id, long matchId) {
    List<LaneAssignmentDomain> assignments = laneAssignmentRepo.findByMatchId(matchId);
    assertThat(assignments).as("%s: an assignment per player", id).hasSize(10);
    List<LaneAssignmentDomain> laning =
        assignments.stream().filter(a -> a.getLane() != null).toList();
    assertThat(laning.size()).as("%s: most heroes have a lane", id).isGreaterThanOrEqualTo(6);
    assertThat(laning)
        .as("%s: windows inside the cap", id)
        .allSatisfy(
            a -> {
              assertThat(a.getStartSeconds()).isBetween(0, a.getEndSeconds());
              assertThat(a.getEndSeconds()).isLessThanOrEqualTo(900);
              assertThat(a.getEndReason()).isNotBlank();
            });
    List<LaneResultDomain> results = laneResultRepo.findByMatchId(matchId);
    assertThat(results.stream().map(LaneResultDomain::getPairId).distinct().count())
        .as("%s: three lanes", id)
        .isEqualTo(3);
    results.stream()
        .filter(r -> !"UNCONTESTED".equals(r.getResult()))
        .forEach(
            r ->
                assertThat(results)
                    .as("%s lane %d: the other side has the opposite margin", id, r.getPairId())
                    .anySatisfy(
                        o -> {
                          assertThat(o.getPairId()).isEqualTo(r.getPairId());
                          assertThat(o.getTeam()).isNotEqualTo(r.getTeam());
                          assertThat(o.getMargin()).isCloseTo(-r.getMargin(), within(1e-9));
                        }));
    assertThat(laneReasonRepo.findByMatchId(matchId))
        .as("%s: reasons are ranked from 1", id)
        .isNotEmpty()
        .anyMatch(r -> r.getRank() == 1);
    System.out.printf(
        "LANES %s: %s%n",
        id,
        results.stream()
            .map(
                r ->
                    "%d %s %s %.2f"
                        .formatted(r.getPairId(), r.getTeam(), r.getResult(), r.getMargin()))
            .toList());
  }

  /**
   * Every rune of OpenDota's log is a pickup, bounty gold is paid, and the opening has a winner.
   */
  private void assertRunes(String id, long matchId) throws Exception {
    JsonNode players = new ObjectMapper().readTree(fixture(id + ".json").toFile()).get("players");
    int logged = 0;
    for (JsonNode p : players) {
      logged += p.path("runes_log").size();
    }
    List<RunePickupDomain> pickups = runePickupRepo.findByMatchId(matchId);
    assertThat(pickups).as("%s: one pickup per logged rune", id).hasSize(logged);
    assertThat(pickups)
        .extracting(RunePickupDomain::getRuneType)
        .as("%s: every rune code is known", id)
        .doesNotContain(RuneType.UNKNOWN.name());
    List<RunePickupDomain> bounty =
        pickups.stream().filter(r -> RuneType.BOUNTY.name().equals(r.getRuneType())).toList();
    assertThat(bounty).as("%s: bounty runes", id).isNotEmpty();
    assertThat(bounty.stream().filter(r -> r.getGold() != null && r.getGold() > 0).count())
        .as("%s: bounty runes with their gold", id)
        .isGreaterThanOrEqualTo(bounty.size() * 9L / 10);
    assertThat(pickups.stream().filter(r -> !bounty.contains(r)))
        .as("%s: only a bounty rune pays gold", id)
        .allSatisfy(r -> assertThat(r.getGold()).isNull());
    List<OpeningSummaryDomain> opening = openingSummaryRepo.findByMatchId(matchId);
    assertThat(opening).as("%s: an opening per team", id).hasSize(2);
    List<String> results = opening.stream().map(OpeningSummaryDomain::getResult).sorted().toList();
    assertThat(results)
        .as("%s: one winner or a draw", id)
        .isIn(List.of("LOST", "WON"), List.of("DRAW", "DRAW"));
    assertThat(opening.stream().mapToLong(OpeningSummaryDomain::getBalance).sum())
        .as("%s: the balances are opposite", id)
        .isZero();
    List<FightReasonDomain> reasons = reasonRepo.findByMatchId(matchId);
    List<FightReasonDomain> advantage =
        reasons.stream().filter(r -> "RUNE_ADVANTAGE".equals(r.getCode())).toList();
    for (FightReasonDomain r : advantage) {
      assertThat(reasons)
          .as("%s fight %d: the other side says it", id, r.getFightIndex())
          .anySatisfy(
              e -> {
                assertThat(e.getCode()).isEqualTo("ENEMY_RUNE_ADVANTAGE");
                assertThat(e.getFightIndex()).isEqualTo(r.getFightIndex());
                assertThat(e.getEvidence()).contains(r.getEvidence());
                assertThat(e.getTeam()).isNotEqualTo(r.getTeam());
              });
    }
    System.out.printf(
        "RUNES %s: %d pickups (%d bounty), %d rune advantage reasons, opening %s%n",
        id,
        pickups.size(),
        bounty.size(),
        advantage.size(),
        opening.stream().map(o -> o.getTeam() + " " + o.getResult()).toList());
  }

  /**
   * A row for every hero who held a listed item, and the numbers agree with the stored charges, the
   * combat log and the creep kills they come from.
   */
  private void assertItemEfficiency(String id, long matchId) {
    List<ItemEfficiency> rows =
        itemEfficiencyRepo.findByMatchId(matchId).stream()
            .map(ItemEfficiencyJson::fromRow)
            .toList();
    Map<String, Set<Long>> holders = new TreeMap<>();
    Map<Long, List<ReplayStateChangeDomain>> changes = new HashMap<>();
    for (ReplayStateChangeDomain c : stateRepo.findByMatchIdOrderByGameSeconds(matchId)) {
      changes.computeIfAbsent(c.getPlayerSlot(), k -> new ArrayList<>()).add(c);
      if (StateChangeKind.ITEM_ADDED.name().equals(c.getKind())) {
        Map.of(
                "magic_stick", "magic_stick",
                "magic_wand", "magic_stick",
                "hand_of_midas", "hand_of_midas",
                "bfury", "farming_items",
                "maelstrom", "farming_items",
                "mjollnir", "farming_items")
            .entrySet()
            .stream()
            .filter(e -> c.getName().equals("item_" + e.getKey()))
            .forEach(
                e ->
                    holders
                        .computeIfAbsent(e.getValue(), k -> new TreeSet<>())
                        .add(c.getPlayerSlot()));
      }
    }
    assertThat(holders.keySet()).as("%s: some listed item was bought", id).contains("magic_stick");
    holders.forEach(
        (entry, slots) ->
            assertThat(
                    rows.stream().filter(r -> r.entryKey().equals(entry)).map(ItemEfficiency::slot))
                .as("%s: a row for every hero who held a %s item", id, entry)
                .containsExactlyInAnyOrderElementsOf(slots));
    for (ItemEfficiency r : rows) {
      assertThat(r.cost()).as("%s slot %d: cost from the constants", id, r.slot()).isPositive();
      if (r.ratio() != null) {
        assertThat(r.ratio()).isEqualTo(r.gain() / r.cost());
      }
      assertThat(r.rows()).as("%s slot %d %s: rows", id, r.slot(), r.entryKey()).isNotNull();
    }
    List<ItemEfficiency> sticks =
        rows.stream().filter(r -> r.kind().equals("magic-stick")).toList();
    long uses = 0;
    long followed = 0;
    long withCharges = 0;
    for (ItemEfficiency r : sticks) {
      assertThat(r.figures().get("saves")).isLessThanOrEqualTo((double) r.count());
      assertThat(r.figures().get("hp")).isLessThanOrEqualTo(r.figures().get("charges") * 15);
      for (ItemEfficiency.Detail use : r.rows()) {
        uses++;
        if (use.figures().get("charges") > 0) {
          withCharges++;
          double before = use.figures().get("charges");
          boolean dropped =
              changes.get(r.slot()).stream()
                  .anyMatch(
                      c ->
                          StateChangeKind.ITEM_CHARGES.name().equals(c.getKind())
                              && c.getCharges() * 2 <= before
                              && c.getGameSeconds() >= use.seconds() - 1
                              && c.getGameSeconds() <= use.seconds() + 6);
          followed += dropped ? 1 : 0;
        }
      }
    }
    assertThat(uses).as("%s: stick uses", id).isPositive();
    assertThat(followed)
        .as("%s: uses with charges followed by a drop to half or less (E3 verified)", id)
        .isGreaterThanOrEqualTo(withCharges * 8 / 10);
    for (ItemEfficiency r : rows.stream().filter(r -> r.kind().equals("hand-of-midas")).toList()) {
      assertThat(r.figures().get("gold"))
          .as("%s slot %d: every use pays the bonus gold", id, r.slot())
          .isEqualTo(r.count() * 160.0);
      assertThat(r.paybackSeconds() != null)
          .as("paid back exactly when the gain reached the cost")
          .isEqualTo(r.gain() >= r.cost());
    }
    for (ItemEfficiency r : rows.stream().filter(r -> r.kind().equals("farming-group")).toList()) {
      assertThat(r.rows()).extracting(d -> d.label()).startsWith("BEFORE", "AFTER", "DAMAGE");
      assertThat(r.figures().get("procDamage")).as("damage of the item's own effects").isPositive();
      assertThat(r.figures().get("damageValue")).isGreaterThanOrEqualTo(0.0);
    }
    System.out.printf(
        "ITEMS %s: %d rows (%d stick, %d midas, %d farming), %d stick uses, %.0f saves%n",
        id,
        rows.size(),
        sticks.size(),
        rows.stream().filter(r -> r.kind().equals("hand-of-midas")).count(),
        rows.stream().filter(r -> r.kind().equals("farming-group")).count(),
        uses,
        sticks.stream().mapToDouble(r -> r.figures().get("saves")).sum());
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
    saveImportantFights(id, matchId);
    saveLanes(id, matchId);
    saveBehaviourSections(id, matchId);
    System.out.println(
        "FIGHT LOSS "
            + id
            + ": "
            + explanations.size()
            + " lost fights, "
            + explanations.stream().filter(e -> e.getReasonCount() == 0).count()
            + " without a reason");
  }

  private void saveImportantFights(String id, long matchId) {
    List<String> lines =
        importantFightsSection.lines(new ReplayReportInput(matchId, null, Set.of(), Map.of(), 3));
    String text = String.join("\n", lines);
    System.out.println("IMPORTANT FIGHTS " + id + "\n" + text);
    assertThat(text).as("%s: important fights fit a message", id).hasSizeLessThan(2000);
    List<String> tracked =
        importantFightsSection.lines(new ReplayReportInput(matchId, null, Set.of(0L), Map.of(), 3));
    assertThat(String.join("\n", tracked)).as("%s: your team marker", id).contains("(your team)");
    try {
      Path dir = Path.of(System.getProperty("user.home"), "dbuff-fixtures", "send");
      Files.createDirectories(dir);
      Files.writeString(dir.resolve("section-important-fights-" + id + ".txt"), text + "\n");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void saveLanes(String id, long matchId) {
    List<String> lines =
        lanesSection.lines(new ReplayReportInput(matchId, null, Set.of(), Map.of(), 3));
    String text = String.join("\n", lines);
    System.out.println("LANES " + id + "\n" + text);
    assertThat(lines).as("%s: lanes", id).hasSizeBetween(2, 4);
    assertThat(text).as("%s: lanes fit a message", id).hasSizeLessThan(2000);
    List<String> tracked =
        lanesSection.lines(new ReplayReportInput(matchId, null, Set.of(0L), Map.of(), 3));
    String trackedText = String.join("\n", tracked);
    System.out.println("LANES (slot 0 tracked) " + id + "\n" + trackedText);
    assertThat(trackedText).as("%s: your team marker", id).contains("(your team)");
    try {
      Path dir = Path.of(System.getProperty("user.home"), "dbuff-fixtures", "send");
      Files.createDirectories(dir);
      Files.writeString(dir.resolve("section-lanes-" + id + ".txt"), text + "\n");
      Files.writeString(dir.resolve("section-lanes-" + id + "-your-team.txt"), trackedText + "\n");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void saveBehaviourSections(String id, long matchId) {
    ReplayReportInput input = new ReplayReportInput(matchId, null, Set.of(), Map.of(), 3);
    saveSection("rotations", rotationsSection.lines(input), id, 5);
    saveSection("smoke", smokeSection.lines(input), id, 4);
    saveSection("objectives", objectivesSection.lines(input), id, 7);
    assertThat(endGameRepo.findByMatchId(matchId)).as("%s: end game row", id).hasSize(1);
    System.out.println("END GAME " + id + " " + endGameRepo.findByMatchId(matchId).getFirst());
    saveSection("endgame", endGameSection.lines(input), id, 2);
    saveSection("items", itemsSection.lines(input), id, 5);
    saveSection("outliers", outliersSection.lines(input), id, 5);
    saveSection("opening", openingSection.lines(input), id, 4);
    saveSection("vision", visionSection.lines(input), id, 7);
  }

  private static final java.util.Map<String, String> MID_ACTIVITY_DOC =
      new java.util.concurrent.ConcurrentSkipListMap<>();

  /** Each team's mid hero in plain words with the numbers behind it, for every match run so far. */
  private void saveMidActivity(String id, MatchDomain match) {
    long matchId = match.getId();
    List<com.ako.dbuff.dao.model.MidActivityDomain> rows = midActivityRepo.findByMatchId(matchId);
    assertThat(rows).as("%s: a mid activity row per team", id).hasSize(2);
    assertThat(rows)
        .allSatisfy(
            r -> {
              assertThat(r.getVerdict()).isIn("ACTIVE", "PASSIVE");
              assertThat(r.getWindowStartSeconds()).isLessThan(r.getWindowEndSeconds());
              assertThat(r.getGoldPerMinute()).as("%s: gold per minute", id).isNotNull();
            });
    List<String> lines =
        midActivitySection.lines(new ReplayReportInput(matchId, match, Set.of(), Map.of(), 3));
    assertThat(lines).hasSize(2);
    StringBuilder text = new StringBuilder("## Match " + id + "\n\n");
    lines.forEach(l -> text.append("- ").append(l).append("\n"));
    text.append("\nNumbers behind it:\n\n");
    String format =
        "- %s mid (slot %d): level 6 at %s, next step %s; mid game %s to %s; kills %d,"
            + " assists %d; fights %d of %d (team median %s); rotations %d (%d good);"
            + " gold per minute %d (enemy mid %d, team %d); experience per minute %d"
            + " (enemy mid %d, team %d); verdict %s%n";
    rows.stream()
        .sorted(java.util.Comparator.comparing(r -> r.getPlayerSlot()))
        .forEach(r -> text.append(format.formatted(numbers(r))));
    MID_ACTIVITY_DOC.put(id, text.toString());
    System.out.println("MID ACTIVITY " + id + "\n" + text);
    try {
      Path dir = Path.of(System.getProperty("user.home"), "dbuff-fixtures", "send");
      Files.createDirectories(dir);
      Files.writeString(
          dir.resolve("mid-activity.md"),
          "# Mid hero activity\n\n" + String.join("\n", MID_ACTIVITY_DOC.values()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static final java.util.Map<String, String> SKILL_PICKS_DOC =
      new java.util.concurrent.ConcurrentSkipListMap<>();

  /** The skill-picks section for every player of an Ability Draft match, in plain words. */
  private void saveSkillPicks(String id, MatchDomain match) {
    long matchId = match.getId();
    Set<Long> everyone = Set.of(0L, 1L, 2L, 3L, 4L, 128L, 129L, 130L, 131L, 132L);
    ReplayReportInput input = new ReplayReportInput(matchId, match, everyone, Map.of(), 3);
    assertThat(skillPicksSection.supports(new AbilityDraftProfile())).isTrue();
    assertThat(skillPicksSection.defaultEnabled()).isFalse();
    List<String> lines = skillPicksSection.lines(input);
    assertThat(lines).as("%s: skill picks lines", id).isNotEmpty();
    assertThat(lines).allSatisfy(l -> assertThat(l).doesNotContain("null", "NaN"));
    assertThat(skillPicksSection.view(input)).isPresent();
    StringBuilder text = new StringBuilder("## Match " + id + "\n\n");
    lines.forEach(l -> text.append("- ").append(l).append("\n"));
    SKILL_PICKS_DOC.put(id, text.toString());
    System.out.println("SKILL PICKS " + id + "\n" + text);
    try {
      Path dir = Path.of(System.getProperty("user.home"), "dbuff-fixtures", "send");
      Files.createDirectories(dir);
      Files.writeString(
          dir.resolve("skill-picks.md"),
          "# Skill picks section\n\n" + String.join("\n", SKILL_PICKS_DOC.values()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Object[] numbers(com.ako.dbuff.dao.model.MidActivityDomain r) {
    return new Object[] {
      r.getTeam(),
      r.getPlayerSlot(),
      r.getLevel6Seconds() == null
          ? "never"
          : com.ako.dbuff.service.replay.report.GameTime.clock(r.getLevel6Seconds()),
      r.getNextAction(),
      com.ako.dbuff.service.replay.report.GameTime.clock(r.getWindowStartSeconds()),
      com.ako.dbuff.service.replay.report.GameTime.clock(r.getWindowEndSeconds()),
      r.getKills(),
      r.getAssists(),
      r.getFightsJoined(),
      r.getFightsTotal(),
      r.getTeamMedianFights(),
      r.getRotations(),
      r.getRotationsGood(),
      r.getGoldPerMinute(),
      r.getEnemyGoldPerMinute(),
      r.getTeamGoldPerMinute(),
      r.getXpPerMinute(),
      r.getEnemyXpPerMinute(),
      r.getTeamXpPerMinute(),
      r.getVerdict()
    };
  }

  private void saveSection(String name, List<String> lines, String id, int maxLines) {
    String text = String.join("\n", lines);
    System.out.println(name.toUpperCase() + " " + id + "\n" + text);
    assertThat(lines).as("%s: %s", id, name).hasSizeLessThanOrEqualTo(maxLines);
    assertThat(text).as("%s: %s fit a message", id, name).hasSizeLessThan(2000);
    try {
      Path dir = Path.of(System.getProperty("user.home"), "dbuff-fixtures", "send");
      Files.createDirectories(dir);
      Files.writeString(dir.resolve("section-" + name + "-" + id + ".txt"), text + "\n");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
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
            .filter(r -> !r.getCode().equals("ENEMY_RUNE_ADVANTAGE"))
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

  private void assertHeroDraft(String id, long matchId) {
    List<HeroDraftPickDomain> heroes = heroDraftRepo.findByMatchId(matchId);
    assertThat(heroes).as("%s: 12 pool heroes", id).hasSize(12);
    List<HeroDraftPickDomain> picked =
        heroes.stream().filter(h -> h.getPlayerSlot() != null).toList();
    assertThat(picked).as("%s: 10 hero picks", id).hasSize(10);
    assertThat(picked.stream().map(HeroDraftPickDomain::getPlayerSlot).distinct()).hasSize(10);
    assertThat(picked)
        .allMatch(h -> h.getTurnNumber() != null && h.getClockSeconds() < 0 && h.getTeam() != null);
    List<AbilityDraftPickDomain> skills = draftPickRepo.findByMatchId(matchId);
    assertThat(skills).allMatch(s -> s.getTurnNumber() != null && s.getClockSeconds() < 0);
    assertThat(
            Stream.concat(
                    picked.stream().map(HeroDraftPickDomain::getTurnNumber),
                    skills.stream().map(AbilityDraftPickDomain::getTurnNumber))
                .sorted()
                .toList())
        .as("%s: skill and hero turns are 1 to 50", id)
        .isEqualTo(IntStream.rangeClosed(1, 50).boxed().toList());
    assertThat(matchRepo.findById(matchId).orElseThrow().getGameModeId())
        .as("%s is an Ability Draft fixture", id)
        .isEqualTo(18L);
    assertThat(
            draftBoardSection
                .view(
                    new ReplayReportInput(
                        matchId,
                        matchRepo.findById(matchId).orElseThrow(),
                        java.util.Set.of(0L),
                        java.util.Map.of(0L, "P0"),
                        3))
                .orElseThrow()
                .bullets())
        .as("%s: draft board cards", id)
        .hasSizeGreaterThanOrEqualTo(2);
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

  private BuildFitFixtureReport buildFitReport() {
    return new BuildFitFixtureReport(
        buildFitRepo, roleProfileRepo, playerStatRepo, deathRepo, heroKits);
  }

  private static PlayerRoleProfileDomain stageRow(
      List<PlayerRoleProfileDomain> profiles, long slot, String stage) {
    return profiles.stream()
        .filter(p -> p.getPlayerSlot() == slot && p.getStage().equals(stage))
        .findFirst()
        .orElse(null);
  }
}
