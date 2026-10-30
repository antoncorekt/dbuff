package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.BuildFitDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MissedPickDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.model.SkillPickStatDomain;
import com.ako.dbuff.dao.repo.BuildFitRepo;
import com.ako.dbuff.dao.repo.MissedPickRepo;
import com.ako.dbuff.dao.repo.SkillPickStatRepo;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillStatKind;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillStatNames;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillStatWording;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.AllPickProfile;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillPicksSectionTest {
  private static final Map<Long, String> HEROES = Map.of(0L, "Pudge", 1L, "Lina");
  private static final AbilityLabels LABELS =
      name -> name.equals("lich_chain_frost") ? "Chain Frost" : "Dragon Blood";

  private final SkillPickStatRepo statRepo = mock(SkillPickStatRepo.class);
  private final BuildFitRepo buildRepo = mock(BuildFitRepo.class);
  private final MissedPickRepo missedRepo = mock(MissedPickRepo.class);
  private final ReportHeroNames heroNames = mock(ReportHeroNames.class);
  private final ReplayReportProperties properties = new ReplayReportProperties();
  private final SkillPicksSection section =
      new SkillPicksSection(
          new SkillPickFindings(
              statRepo,
              buildRepo,
              missedRepo,
              new SkillStatWording(
                  new SkillStatNames() {
                    @Override
                    public String hero(long heroId) {
                      return "Pudge";
                    }

                    @Override
                    public String skill(long abilityId) {
                      return abilityId == 1 ? "Storm Bolt" : "Thunder Clap";
                    }
                  }),
              LABELS,
              heroNames,
              properties));

  private static SkillPickStatDomain stat(SkillStatKind kind, long first, long games, long wins) {
    return SkillPickStatDomain.builder()
        .matchId(1L)
        .playerSlot(0L)
        .kind(kind)
        .firstId(first)
        .games(games)
        .wins(wins)
        .windowMonths(12)
        .build();
  }

  private static MissedPickDomain missed(long slot, double ratio) {
    return MissedPickDomain.builder()
        .matchId(1L)
        .playerSlot(slot)
        .takenAbility("dragon_knight_dragon_blood")
        .takenTurn(12)
        .candidateAbility("lich_chain_frost")
        .candidateTurn(31)
        .basis("DAMAGE")
        .ratio(ratio)
        .enemyWonFights(4)
        .candidateWonFights(3)
        .takenWonFights(1)
        .build();
  }

  private static BuildFitDomain build(String stated, String evidence) {
    return BuildFitDomain.builder()
        .matchId(1L)
        .playerSlot(0L)
        .stage("BOTH")
        .stated(stated)
        .evidence(evidence)
        .build();
  }

  private ReplayReportInput input(int maxFindings, Long... focus) {
    when(heroNames.iconBySlot(1L)).thenReturn(HEROES);
    MatchDomain match = MatchDomain.builder().id(1L).gameModeId(18L).build();
    return new ReplayReportInput(
        1L, match, Set.of(focus), Map.of(0L, "Anton (Pudge)", 1L, "Olga (Lina)"), maxFindings);
  }

  @Test
  void isOffByDefaultAndAbilityDraftOnlyAtStandardDetail() {
    assertThat(section.name()).isEqualTo("skill-picks");
    assertThat(section.defaultEnabled()).isFalse();
    assertThat(section.minimumDetail()).isEqualTo(ReportDetail.STANDARD);
    assertThat(section.supports(new AbilityDraftProfile())).isTrue();
    assertThat(section.supports(new AllPickProfile())).isFalse();
    assertThat(section.order()).isEqualTo(13);
  }

  @Test
  void saysNothingWithoutStoredRows() {
    assertThat(section.lines(input(3, 0L))).isEmpty();
    assertThat(section.view(input(3, 0L))).isEmpty();
  }

  @Test
  void keepsTheBestMissedPickTheBuildGapAndTheStrongestStatisticOfEachPlayer() {
    when(missedRepo.findByMatchId(1L)).thenReturn(List.of(missed(0, 1.2), missed(0, 1.38)));
    when(buildRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                build("", "Pudge's build fit the roaming support role."),
                build("ESCAPE", "Pudge had no escape and died alone 4 times.")));
    when(statRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                stat(SkillStatKind.HERO, 1, 40, 30),
                stat(SkillStatKind.SKILL, 1, 2, 1),
                stat(SkillStatKind.SKILL, 2, 4, 4)));

    List<Finding> findings = section.findings(input(3, 0L));

    assertThat(findings)
        .extracting(Finding::kind)
        .containsExactly(FindingKind.MISSED, FindingKind.BUILD, FindingKind.PICK);
    assertThat(findings.get(0).text())
        .startsWith("Anton (Pudge): The enemy took Chain Frost at turn 31; it did 38% more");
    assertThat(findings.get(1).text())
        .isEqualTo("Anton (Pudge): Pudge had no escape and died alone 4 times.");
    assertThat(findings.get(2).text()).isEqualTo("Anton (Pudge): Pudge: 75% in 40 games");
  }

  @Test
  void smallSamplesAreShownWithTheirCountAndNeverOutrankASolidOne() {
    when(statRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                stat(SkillStatKind.SKILL, 2, 5, 5),
                stat(SkillStatKind.HERO, 1, 40, 30),
                stat(SkillStatKind.SKILL, 1, 1, 1)));

    assertThat(section.lines(input(2, 0L)))
        .containsExactly(
            "Anton (Pudge): Pudge: 75% in 40 games",
            "Anton (Pudge): Thunder Clap: 100% in only 5 games");
  }

  @Test
  void aFittingBuildAndRowsOfOtherPlayersAreLeftOut() {
    when(buildRepo.findByMatchId(1L))
        .thenReturn(List.of(build("", "Pudge's build fit the roaming support role.")));
    when(missedRepo.findByMatchId(1L)).thenReturn(List.of(missed(1, 1.5)));

    assertThat(section.lines(input(3, 0L))).isEmpty();
    assertThat(section.lines(input(3, 1L))).hasSize(1).allMatch(l -> l.startsWith("Olga (Lina)"));
  }

  @Test
  void aLongLineEndsInAnEllipsisAtTheConfiguredLength() {
    properties.setMaxSkillPickLineLength(60);
    when(buildRepo.findByMatchId(1L)).thenReturn(List.of(build("ESCAPE", "word ".repeat(40))));

    assertThat(section.lines(input(3, 0L)))
        .singleElement()
        .satisfies(
            l -> {
              assertThat(l.length()).isLessThanOrEqualTo(60);
              assertThat(l).endsWith(LineText.ELLIPSIS);
            });
  }

  @Test
  void theViewHoldsTheLines() {
    when(missedRepo.findByMatchId(1L)).thenReturn(List.of(missed(0, 1.38)));

    assertThat(section.view(input(3, 0L)))
        .hasValueSatisfying(
            v -> {
              assertThat(v.title()).isEqualTo("Skill picks");
              assertThat(v.bullets()).hasSize(1);
            });
  }

  @Test
  void theLineLengthIsValidated() {
    for (int bad : new int[] {59, 601}) {
      properties.setMaxSkillPickLineLength(bad);
      assertThatThrownBy(properties::validate).hasMessageContaining("max-skill-pick-line-length");
    }
  }

  @Test
  void rowsCloseToACoinFlipAreDroppedInsteadOfFillingTheSlots() {
    when(statRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                stat(SkillStatKind.SKILL, 1, 2, 1),
                stat(SkillStatKind.SKILL, 2, 1, 1),
                stat(SkillStatKind.HERO, 1, 10, 5)));

    assertThat(section.lines(input(3, 0L))).isEmpty();
  }

  @Test
  void aNameWithoutAKnownHeroIsPrintedAlone() {
    when(heroNames.iconBySlot(1L)).thenReturn(Map.of());
    when(missedRepo.findByMatchId(1L)).thenReturn(List.of(missed(0, 1.38)));
    MatchDomain match = MatchDomain.builder().id(1L).gameModeId(18L).build();

    assertThat(section.lines(new ReplayReportInput(1L, match, Set.of(0L), Map.of(0L, "Anton"), 3)))
        .singleElement()
        .satisfies(l -> assertThat(l).startsWith("Anton: The enemy").doesNotContain("null"));
  }

  @Test
  void theBuildRowIsChosenByStageThenId() {
    BuildFitDomain intent = build("ESCAPE", "Drafted as carry: Pudge had no escape.");
    intent.setStage("INTENT");
    intent.setId(9L);
    BuildFitDomain played = build("ESCAPE", "Played mid: Pudge had no farming skill.");
    played.setStage("FINAL");
    played.setId(1L);
    when(buildRepo.findByMatchId(1L)).thenReturn(List.of(played, intent));

    assertThat(section.lines(input(3, 0L)))
        .containsExactly("Anton (Pudge): Played mid: Pudge had no farming skill.");
  }

  @Test
  void aClearDamageGapBeatsFightsAndOtherwiseMoreWonFightsWin() {
    MissedPickDomain clear = missed(0, 1.6);
    clear.setCandidateWonFights(1);
    MissedPickDomain fights = missed(0, 0);
    fights.setRatio(null);
    fights.setBasis("FIGHTS");
    fights.setCandidateWonFights(4);
    MissedPickDomain modest = missed(0, 1.2);
    modest.setCandidateWonFights(2);

    assertThat(
            java.util.stream.Stream.of(fights, modest, clear).max(SkillPickFindings.MISSED_ORDER))
        .containsSame(clear);
    MissedPickDomain carried = missed(0, 3.0);
    carried.setCandidateWonFights(5);
    carried.setCarried(true);
    assertThat(
            java.util.stream.Stream.of(carried, modest, fights).max(SkillPickFindings.MISSED_ORDER))
        .containsSame(fights);
    assertThat(java.util.stream.Stream.of(carried, modest).max(SkillPickFindings.MISSED_ORDER))
        .containsSame(modest);
    assertThat(java.util.stream.Stream.of(modest, fights).max(SkillPickFindings.MISSED_ORDER))
        .containsSame(fights);
    MissedPickDomain twin = missed(0, 1.2);
    twin.setCandidateWonFights(2);
    twin.setTakenTurn(5);
    assertThat(java.util.stream.Stream.of(modest, twin).max(SkillPickFindings.MISSED_ORDER))
        .containsSame(twin);
  }

  @Test
  void aGapRowIsListedBeforeAGapsNotShownRowAndAFitRowNever() {
    BuildFitDomain notShown = build("ESCAPE", "Not shown: Pudge may lack escape.");
    notShown.setStage("BOTH");
    notShown.setVerdict("GAPS_NOT_SHOWN");
    BuildFitDomain gap = build("ESCAPE", "Gap: Pudge had no escape and died alone.");
    gap.setStage("FINAL");
    gap.setVerdict("GAP");
    BuildFitDomain fit = build("ESCAPE", "Fit: Pudge was fine.");
    fit.setStage("INTENT");
    fit.setVerdict("FIT");
    when(buildRepo.findByMatchId(1L)).thenReturn(List.of(notShown, fit, gap));
    assertThat(section.lines(input(3, 0L)))
        .containsExactly("Anton (Pudge): Gap: Pudge had no escape and died alone.");

    when(buildRepo.findByMatchId(1L)).thenReturn(List.of(fit, notShown));
    assertThat(section.lines(input(3, 0L)))
        .containsExactly("Anton (Pudge): Not shown: Pudge may lack escape.");

    when(buildRepo.findByMatchId(1L)).thenReturn(List.of(fit));
    assertThat(section.lines(input(3, 0L))).isEmpty();
  }
}
