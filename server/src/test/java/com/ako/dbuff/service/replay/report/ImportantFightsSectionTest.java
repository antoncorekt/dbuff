package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightExplanationDomain;
import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.dao.model.FightReasonDomain;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.LostFight;
import com.ako.dbuff.service.replay.analysis.fightloss.FightExplanationReader.RankedReason;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImportantFightsSectionTest {

  private final FightExplanationReader reader = mock(FightExplanationReader.class);
  private final ReportHeroNames heroNames = mock(ReportHeroNames.class);
  private final ReplayReportProperties properties = new ReplayReportProperties();
  private final SectionSwitch sectionSwitch = new SectionSwitch(properties, List.of());
  private final ImportantFightsSection section =
      new ImportantFightsSection(
          reader, heroNames, new ReasonWording(Prettify::ability), properties);

  ImportantFightsSectionTest() {
    when(heroNames.bySlot(1L)).thenReturn(Map.of(2L, "Terrorblade", 130L, "Venomancer"));
  }

  private static RankedReason reason(String code, long slot) {
    return new RankedReason(
        1,
        FightReasonDomain.builder()
            .scope("PLAYER")
            .subjectSlot(slot)
            .code(code)
            .evidence("")
            .severity(0.5)
            .build(),
        List.of(),
        true);
  }

  private static LostFight fight(
      int index, double start, String winner, Long gold, boolean flip, RankedReason... reasons) {
    return new LostFight(
        FightDomain.builder().fightIndex(index).startSeconds(start).build(),
        FightExplanationDomain.builder()
            .fightIndex(index)
            .losingTeam("RADIANT".equals(winner) ? "DIRE" : "RADIANT")
            .build(),
        List.of(reasons),
        reasons.length,
        FightImportanceDomain.builder()
            .fightIndex(index)
            .winningTeam(winner)
            .goldChange(gold)
            .leadFlip(flip)
            .flipToWinner(flip)
            .important(true)
            .build());
  }

  private void stored(LostFight... fights) {
    when(reader.importantFights(1L)).thenReturn(List.of(fights));
    when(reader.lostFights(anyLong(), any(), anyInt()))
        .thenReturn(
            List.of(fights).stream()
                .collect(Collectors.toMap(l -> l.fight().getFightIndex(), l -> l)));
  }

  private ReplayReportInput input(Long... focus) {
    return new ReplayReportInput(1L, null, Set.of(focus), Map.of(), 3);
  }

  @Test
  void isOffByDefaultAndShipsNamedImportantFights() {
    assertThat(section.name()).isEqualTo("important-fights");
    assertThat(section.defaultEnabled()).isFalse();
    assertThat(sectionSwitch.isEnabled(section)).isFalse();

    properties.setSections(Map.of("important-fights", toggle(true)));

    assertThat(sectionSwitch.isEnabled(section)).isTrue();
  }

  @Test
  void tellsWinnerWorthLeadFlipAndReasonsInTimeOrder() {
    stored(
        fight(3, 1500, "DIRE", 4200L, true, reason("DIED_FIRST", 2)),
        fight(1, 600, "RADIANT", 1200L, false, reason("NO_TP", 130), reason("BURST", 130)),
        fight(2, 1020, "DIRE", 2500L, false));

    List<String> lines = section.lines(input(2L, 130L));

    assertThat(lines)
        .containsExactly(
            "**Important fights**",
            "10:00 Radiant won the fight, 1.2k net worth swing: Venomancer had no teleport,"
                + " Venomancer was burst down",
            "17:00 Dire won the fight, 2.5k net worth swing",
            "25:00 Dire won the fight, 4.2k net worth swing, took the lead: Terrorblade died"
                + " first");
  }

  @Test
  void marksTheTrackedSideWhenAllTrackedPlayersShareIt() {
    stored(fight(1, 600, "DIRE", 1200L, false));

    assertThat(section.lines(input(130L)))
        .contains("10:00 Dire (your team) won the fight, 1.2k net worth swing");
    assertThat(section.lines(input(2L, 130L)))
        .contains("10:00 Dire won the fight, 1.2k net worth swing");
  }

  @Test
  void repeatsTheReasonsOfAFightTheSwingSectionAlsoTells() {
    stored(fight(1, 600, "RADIANT", 1200L, false, reason("NO_TP", 130)));

    assertThat(section.lines(input(2L)).get(1)).endsWith("Venomancer had no teleport");
  }

  @Test
  void skipsAFightWithoutAKnownWinner() {
    stored(fight(1, 600, null, 1200L, false), fight(2, 700, "DIRE", 1200L, false));

    List<String> lines = section.lines(input(2L));

    assertThat(lines).hasSize(2);
    assertThat(lines.get(1)).startsWith("11:40 Dire");
  }

  @Test
  void ranksByTheAbsoluteChangeAndPrintsNoSwingForAnUnknownOrTinyOne() {
    properties.setMaxImportantFightLines(3);
    stored(
        fight(1, 100, "DIRE", -3000L, false),
        fight(2, 200, "DIRE", 40L, false),
        fight(3, 300, "DIRE", 1000L, false),
        fight(4, 400, "DIRE", null, false));

    List<String> lines = section.lines(input(2L));

    assertThat(lines).hasSize(4);
    assertThat(lines.get(1)).contains("3.0k net worth swing");
    assertThat(lines.get(2)).isEqualTo("3:20 Dire won the fight");
    assertThat(lines.get(3)).contains("1.0k net worth swing");
  }

  @Test
  void keepsTheCostliestFightsWithinTheCapAndStillListsThemInTimeOrder() {
    properties.setMaxImportantFightLines(2);
    stored(
        IntStream.range(0, 5)
            .mapToObj(i -> fight(i, 600 + i * 100, "RADIANT", 1000L + i * 1000L, false))
            .toArray(LostFight[]::new));

    List<String> lines = section.lines(input(2L));

    assertThat(lines).hasSize(3);
    assertThat(lines.get(1)).startsWith("15:00").contains("4.0k");
    assertThat(lines.get(2)).startsWith("16:40").contains("5.0k");
  }

  @Test
  void isEmptyWithoutImportantFights() {
    when(reader.importantFights(1L)).thenReturn(List.of());

    assertThat(section.lines(input(2L))).isEmpty();
  }

  @Test
  void rejectsACapBelowOne() {
    for (int bad : new int[] {0, 16}) {
      properties.setMaxImportantFightLines(bad);

      assertThatThrownBy(properties::validate)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("max-important-fight-lines");
    }
    properties.setMaxImportantFightLines(15);
    properties.validate();
  }

  private static ReplayReportProperties.SectionToggle toggle(boolean on) {
    ReplayReportProperties.SectionToggle toggle = new ReplayReportProperties.SectionToggle();
    toggle.setEnabled(on);
    return toggle;
  }
}
