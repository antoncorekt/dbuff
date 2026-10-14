package com.ako.dbuff.service.replay.read;

import com.ako.dbuff.dao.model.EndGameSummaryDomain;
import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightExplanationDomain;
import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.dao.model.FightVisionDomain;
import com.ako.dbuff.dao.model.ItemEfficiencyDomain;
import com.ako.dbuff.dao.model.LaneAssignmentDomain;
import com.ako.dbuff.dao.model.LaneReasonDomain;
import com.ako.dbuff.dao.model.LaneResultDomain;
import com.ako.dbuff.dao.model.ObjectiveConversionDomain;
import com.ako.dbuff.dao.model.OpeningSummaryDomain;
import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.dao.model.RunePickupDomain;
import com.ako.dbuff.dao.model.SmokeUseDomain;
import com.ako.dbuff.dao.model.TeamCompositionDomain;
import com.ako.dbuff.dao.model.TeamLaneSwapDomain;
import com.ako.dbuff.dao.model.TeamRotationDomain;
import com.ako.dbuff.dao.model.TeamSplitStretchDomain;
import com.ako.dbuff.dao.repo.EndGameSummaryRepo;
import com.ako.dbuff.dao.repo.FightExplanationRepo;
import com.ako.dbuff.dao.repo.FightImportanceRepo;
import com.ako.dbuff.dao.repo.FightReasonRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.FightVisionRepo;
import com.ako.dbuff.dao.repo.ItemEfficiencyRepo;
import com.ako.dbuff.dao.repo.LaneAssignmentRepo;
import com.ako.dbuff.dao.repo.LaneReasonRepo;
import com.ako.dbuff.dao.repo.LaneResultRepo;
import com.ako.dbuff.dao.repo.ObjectiveConversionRepo;
import com.ako.dbuff.dao.repo.OpeningSummaryRepo;
import com.ako.dbuff.dao.repo.PlayerRoleRepo;
import com.ako.dbuff.dao.repo.RunePickupRepo;
import com.ako.dbuff.dao.repo.SmokeUseRepo;
import com.ako.dbuff.dao.repo.TeamCompositionRepo;
import com.ako.dbuff.dao.repo.TeamLaneSwapRepo;
import com.ako.dbuff.dao.repo.TeamRotationRepo;
import com.ako.dbuff.dao.repo.TeamSplitStretchRepo;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader;
import com.ako.dbuff.service.replay.analysis.fightloss.FightLossProperties;
import com.ako.dbuff.service.replay.read.BehaviourSectionReader.Behaviour;
import com.ako.dbuff.service.replay.read.FightsSectionReader.ImportantFight;
import com.ako.dbuff.service.replay.read.LanesSectionReader.Lanes;
import com.ako.dbuff.service.replay.read.RolesSectionReader.Roles;
import com.ako.dbuff.service.replay.read.RunesSectionReader.Runes;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@ActiveProfiles("test")
@Import({
  RolesSectionReader.class,
  FightsSectionReader.class,
  VisionSectionReader.class,
  RunesSectionReader.class,
  LanesSectionReader.class,
  ItemsSectionReader.class,
  EndGameSectionReader.class,
  BehaviourSectionReader.class,
  FightExplanationReader.class,
  FightLossProperties.class
})
class SectionReadersTest {
  private static final long MATCH = 91L;
  private static final long OTHER = 92L;

  @Autowired private RolesSectionReader roles;
  @Autowired private FightsSectionReader fights;
  @Autowired private VisionSectionReader vision;
  @Autowired private RunesSectionReader runes;
  @Autowired private LanesSectionReader lanes;
  @Autowired private ItemsSectionReader items;
  @Autowired private EndGameSectionReader endGame;
  @Autowired private EndGameSummaryRepo endGameRepo;
  @Autowired private BehaviourSectionReader behaviour;

  @Autowired private PlayerRoleRepo roleRepo;
  @Autowired private TeamCompositionRepo compositionRepo;
  @Autowired private FightRepo fightRepo;
  @Autowired private FightExplanationRepo explanationRepo;
  @Autowired private FightImportanceRepo importanceRepo;
  @Autowired private FightReasonRepo reasonRepo;
  @Autowired private FightVisionRepo visionRepo;
  @Autowired private RunePickupRepo pickupRepo;
  @Autowired private OpeningSummaryRepo openingRepo;
  @Autowired private LaneAssignmentRepo assignmentRepo;
  @Autowired private LaneResultRepo resultRepo;
  @Autowired private LaneReasonRepo laneReasonRepo;
  @Autowired private ItemEfficiencyRepo itemRepo;
  @Autowired private TeamRotationRepo rotationRepo;
  @Autowired private TeamSplitStretchRepo stretchRepo;
  @Autowired private TeamLaneSwapRepo swapRepo;
  @Autowired private SmokeUseRepo smokeRepo;
  @Autowired private ObjectiveConversionRepo objectiveRepo;

  @Test
  void aMatchWithNoRowsGivesEmptyListsForEverySection() {
    assertThat(roles.read(OTHER, null).players()).isEmpty();
    assertThat(roles.read(OTHER, null).compositions()).isEmpty();
    assertThat(fights.read(OTHER, null)).isEmpty();
    assertThat(vision.read(OTHER, null)).isEmpty();
    assertThat(runes.read(OTHER, null).pickups()).isEmpty();
    assertThat(runes.read(OTHER, null).openings()).isEmpty();
    assertThat(lanes.read(OTHER, null).results()).isEmpty();
    assertThat(items.read(OTHER, null)).isEmpty();
    assertThat(endGame.read(OTHER, null)).isEmpty();
    Behaviour none = behaviour.read(OTHER, null);
    assertThat(none.rotations()).isEmpty();
    assertThat(none.splitStretches()).isEmpty();
    assertThat(none.laneSwaps()).isEmpty();
    assertThat(none.smokes()).isEmpty();
    assertThat(none.objectiveConversions()).isEmpty();
  }

  @Test
  void roles_listPlayersBySlotAndCompositions() {
    roleRepo.save(
        PlayerRoleDomain.builder()
            .matchId(MATCH)
            .playerSlot(129L)
            .role(2)
            .source("FINAL")
            .confidence(0.8)
            .build());
    roleRepo.save(
        PlayerRoleDomain.builder().matchId(MATCH).playerSlot(1L).role(1).source("FINAL").build());
    roleRepo.save(PlayerRoleDomain.builder().matchId(OTHER).playerSlot(3L).role(3).build());
    compositionRepo.save(
        TeamCompositionDomain.builder()
            .matchId(MATCH)
            .team("DIRE")
            .stage("FINAL")
            .tokens("[\"A\"]")
            .summary("s")
            .build());

    Roles read = roles.read(MATCH, null);

    assertThat(read.players())
        .extracting("slot", "role")
        .containsExactly(tuple(1L, 1), tuple(129L, 2));
    assertThat(read.players().get(1).confidence()).isEqualTo(0.8);
    assertThat(read.compositions())
        .extracting("team", "stage")
        .containsExactly(tuple("DIRE", "FINAL"));
  }

  @Test
  void fights_tellImportantOnesWithRankedReasonsAndImportance() {
    fight(1, true);
    fight(2, false);
    reasonRepo.save(
        FightReasonDomain.builder()
            .matchId(MATCH)
            .fightIndex(1)
            .team("RADIANT")
            .scope("PLAYER")
            .subjectSlot(2L)
            .code("DEAD_EARLY")
            .severity(0.9)
            .evidence("{}")
            .build());

    List<ImportantFight> read = fights.read(MATCH, null);

    assertThat(read).hasSize(1);
    ImportantFight first = read.get(0);
    assertThat(first.fightIndex()).isEqualTo(1);
    assertThat(first.losingTeam()).isEqualTo("RADIANT");
    assertThat(first.goldLost()).isEqualTo(900L);
    assertThat(first.importance().goldChange()).isEqualTo(1500L);
    assertThat(first.importance().leadFlip()).isTrue();
    assertThat(first.reasonsShown()).isEqualTo(1);
    assertThat(first.totalReasons()).isEqualTo(1);
    assertThat(first.reasons()).hasSize(1);
    assertThat(first.reasons().get(0).rank()).isEqualTo(1);
    assertThat(first.reasons().get(0).code()).isEqualTo("DEAD_EARLY");
    assertThat(first.reasons().get(0).subjectSlot()).isEqualTo(2L);
  }

  @Test
  void fights_anImportantFightWithoutAnExplanationStillAppears() {
    fightRepo.save(FightDomain.builder().matchId(MATCH).fightIndex(7).type("SKIRMISH").build());
    importanceRepo.save(
        FightImportanceDomain.builder()
            .matchId(MATCH)
            .fightIndex(7)
            .winningTeam("DIRE")
            .important(true)
            .build());
    fightRepo.save(FightDomain.builder().matchId(MATCH).fightIndex(8).build());
    importanceRepo.save(
        FightImportanceDomain.builder().matchId(MATCH).fightIndex(8).important(false).build());

    List<ImportantFight> read = fights.read(MATCH, null);

    assertThat(read).hasSize(1);
    assertThat(read.get(0).fightIndex()).isEqualTo(7);
    assertThat(read.get(0).losingTeam()).isNull();
    assertThat(read.get(0).goldLost()).isNull();
    assertThat(read.get(0).totalReasons()).isZero();
    assertThat(read.get(0).reasonsShown()).isZero();
    assertThat(read.get(0).reasons()).isEmpty();
    assertThat(read.get(0).importance().winningTeam()).isEqualTo("DIRE");
  }

  @Test
  void fights_cutTheReasonsToTheTopFiveAndSayHowManyWereShown() {
    fight(1, true);
    for (int i = 0; i < 8; i++) {
      reason(1, 10L + i, "R" + i, 0.9 - i * 0.01);
    }

    ImportantFight first = fights.read(MATCH, null).get(0);

    assertThat(first.totalReasons()).isEqualTo(8);
    assertThat(first.reasonsShown()).isEqualTo(FightsSectionReader.TOP_REASONS);
    assertThat(first.reasons()).hasSize(FightsSectionReader.TOP_REASONS);
  }

  @Test
  void rowsWithEqualSortKeys_keepTheOrderTheyWereStored() {
    for (String verdict : List.of("FIRST", "SECOND", "THIRD")) {
      rotationRepo.save(
          TeamRotationDomain.builder()
              .matchId(MATCH)
              .team("DIRE")
              .playerSlot(130L)
              .startSeconds(600)
              .verdict(verdict)
              .build());
      smokeRepo.save(
          SmokeUseDomain.builder()
              .matchId(MATCH)
              .team("DIRE")
              .startSeconds(5)
              .outcome(verdict)
              .build());
      laneReasonRepo.save(
          LaneReasonDomain.builder()
              .matchId(MATCH)
              .pairId(1)
              .team("RADIANT")
              .rank(1)
              .code(verdict)
              .build());
    }
    resultRepo.save(
        LaneResultDomain.builder().matchId(MATCH).pairId(1).team("RADIANT").lane("TOP").build());

    Behaviour read = behaviour.read(MATCH, null);

    assertThat(read.rotations()).extracting("verdict").containsExactly("FIRST", "SECOND", "THIRD");
    assertThat(read.smokes()).extracting("outcome").containsExactly("FIRST", "SECOND", "THIRD");
    assertThat(lanes.read(MATCH, null).results().get(0).reasons())
        .extracting("code")
        .containsExactly("FIRST", "SECOND", "THIRD");
  }

  @Test
  void vision_listsRowsByFightAndTeam() {
    visionRepo.save(vision(2, "DIRE"));
    visionRepo.save(vision(1, "RADIANT"));

    assertThat(vision.read(MATCH, null))
        .extracting("fightIndex", "team", "lost")
        .containsExactly(tuple(1, "RADIANT", true), tuple(2, "DIRE", true));
  }

  @Test
  void runes_listPickupsInTimeOrderAndOpenings() {
    pickupRepo.save(pickup(240, 1));
    pickupRepo.save(pickup(120, 0));
    openingRepo.save(
        OpeningSummaryDomain.builder()
            .matchId(MATCH)
            .team("RADIANT")
            .runesTaken(1)
            .balance(300L)
            .result("AHEAD")
            .build());

    Runes read = runes.read(MATCH, null);

    assertThat(read.pickups()).extracting("seconds").containsExactly(120, 240);
    assertThat(read.openings()).extracting("balance").containsExactly(300L);
  }

  @Test
  void lanes_resultsCarryPartnersMarginAndReasons() {
    assignmentRepo.save(assignment(3L, "RADIANT", 1));
    assignmentRepo.save(assignment(1L, "RADIANT", 1));
    assignmentRepo.save(assignment(129L, "DIRE", 1));
    resultRepo.save(
        LaneResultDomain.builder()
            .matchId(MATCH)
            .pairId(1)
            .team("RADIANT")
            .lane("TOP")
            .result("WON")
            .margin(0.42)
            .build());
    laneReasonRepo.save(
        LaneReasonDomain.builder()
            .matchId(MATCH)
            .pairId(1)
            .team("RADIANT")
            .rank(1)
            .code("CREEP_EDGE")
            .severity(0.5)
            .build());

    Lanes read = lanes.read(MATCH, null);

    assertThat(read.assignments()).hasSize(3);
    assertThat(read.results()).hasSize(1);
    assertThat(read.results().get(0).slots()).containsExactlyInAnyOrder(1L, 3L);
    assertThat(read.results().get(0).margin()).isEqualTo(0.42);
    assertThat(read.results().get(0).reasons()).extracting("code").containsExactly("CREEP_EDGE");
  }

  @Test
  void endGame_listsTheSummaryOfTheMatchOnly() {
    endGameRepo.save(
        EndGameSummaryDomain.builder()
            .matchId(MATCH)
            .winnerTeam("RADIANT")
            .peakLeadGold(8300L)
            .thrown(true)
            .direBuybacks(3)
            .build());
    endGameRepo.save(EndGameSummaryDomain.builder().matchId(OTHER).winnerTeam("DIRE").build());

    assertThat(endGame.read(MATCH, null))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.winnerTeam()).isEqualTo("RADIANT");
              assertThat(e.peakLeadGold()).isEqualTo(8300L);
              assertThat(e.thrown()).isTrue();
              assertThat(e.direBuybacks()).isEqualTo(3);
            });
    assertThat(endGame.name()).isEqualTo("endgame");
  }

  @Test
  void items_listEfficiencyRows() {
    itemRepo.save(
        ItemEfficiencyDomain.builder()
            .matchId(MATCH)
            .playerSlot(1L)
            .entryKey("a")
            .itemKey("magic_stick")
            .kind("STICK")
            .ratio(0.5)
            .build());

    assertThat(items.read(MATCH, null))
        .extracting("itemKey", "ratio")
        .containsExactly(tuple("magic_stick", 0.5));
  }

  @Test
  void behaviour_listsEveryTopic() {
    rotationRepo.save(
        TeamRotationDomain.builder()
            .matchId(MATCH)
            .team("DIRE")
            .playerSlot(130L)
            .startSeconds(600)
            .reasons("[\"X\"]")
            .verdict("PAID")
            .build());
    stretchRepo.save(
        TeamSplitStretchDomain.builder()
            .matchId(MATCH)
            .team("DIRE")
            .startSeconds(700)
            .label("SPLIT")
            .build());
    swapRepo.save(
        TeamLaneSwapDomain.builder()
            .matchId(MATCH)
            .team("DIRE")
            .playerSlot(130L)
            .laneBefore("TOP")
            .laneAfter("BOTTOM")
            .build());
    smokeRepo.save(
        SmokeUseDomain.builder()
            .matchId(MATCH)
            .team("DIRE")
            .userSlot(130L)
            .outcome("KILL")
            .build());
    objectiveRepo.save(
        ObjectiveConversionDomain.builder()
            .matchId(MATCH)
            .fightIndex(1)
            .winnerTeam("DIRE")
            .objectiveKind("TOWER")
            .build());

    Behaviour read = behaviour.read(MATCH, null);

    assertThat(read.rotations())
        .extracting("reasons", "verdict")
        .containsExactly(tuple("[\"X\"]", "PAID"));
    assertThat(read.splitStretches()).extracting("label").containsExactly("SPLIT");
    assertThat(read.laneSwaps()).extracting("laneAfter").containsExactly("BOTTOM");
    assertThat(read.smokes()).extracting("outcome").containsExactly("KILL");
    assertThat(read.objectiveConversions()).extracting("objectiveKind").containsExactly("TOWER");
  }

  private void reason(int fight, long slot, String code, double severity) {
    reasonRepo.save(
        FightReasonDomain.builder()
            .matchId(MATCH)
            .fightIndex(fight)
            .team("RADIANT")
            .scope("PLAYER")
            .subjectSlot(slot)
            .code(code)
            .severity(severity)
            .evidence("e")
            .build());
  }

  private void fight(int index, boolean important) {
    fightRepo.save(
        FightDomain.builder().matchId(MATCH).fightIndex(index).type("TEAMFIGHT").build());
    explanationRepo.save(
        FightExplanationDomain.builder()
            .matchId(MATCH)
            .fightIndex(index)
            .losingTeam("RADIANT")
            .goldLost(900L)
            .reasonCount(1)
            .build());
    importanceRepo.save(
        FightImportanceDomain.builder()
            .matchId(MATCH)
            .fightIndex(index)
            .winningTeam("DIRE")
            .goldChange(1500L)
            .leadFlip(true)
            .important(important)
            .build());
  }

  private FightVisionDomain vision(int fight, String team) {
    return FightVisionDomain.builder()
        .matchId(MATCH)
        .fightIndex(fight)
        .team(team)
        .lost(true)
        .verdict("NONE")
        .build();
  }

  private RunePickupDomain pickup(int seconds, int index) {
    return RunePickupDomain.builder()
        .matchId(MATCH)
        .playerSlot(1L)
        .team("RADIANT")
        .runeIndex(index)
        .runeType("HASTE")
        .seconds(seconds)
        .build();
  }

  private LaneAssignmentDomain assignment(long slot, String team, int pair) {
    return LaneAssignmentDomain.builder()
        .matchId(MATCH)
        .playerSlot(slot)
        .team(team)
        .lane("TOP")
        .pairId(pair)
        .startSeconds(0)
        .build();
  }
}
