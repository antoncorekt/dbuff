package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.OpeningSummaryDomain;
import com.ako.dbuff.dao.model.RunePickupDomain;
import com.ako.dbuff.dao.repo.OpeningSummaryRepo;
import com.ako.dbuff.dao.repo.RunePickupRepo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpeningSectionTest {
  private final OpeningSummaryRepo summaryRepo = mock(OpeningSummaryRepo.class);
  private final RunePickupRepo pickupRepo = mock(RunePickupRepo.class);
  private final ReportHeroNames heroNames = mock(ReportHeroNames.class);
  private final ReplayReportProperties properties = new ReplayReportProperties();
  private final OpeningSection section =
      new OpeningSection(summaryRepo, pickupRepo, heroNames, properties);
  private final List<OpeningSummaryDomain> summaries = new ArrayList<>();
  private final List<RunePickupDomain> pickups = new ArrayList<>();
  private long nextId = 1;

  OpeningSectionTest() {
    when(summaryRepo.findByMatchId(1L)).thenReturn(summaries);
    when(pickupRepo.findByMatchId(1L)).thenReturn(pickups);
    when(heroNames.iconBySlot(1L))
        .thenReturn(Map.of(0L, "Axe", 1L, "Lion", 128L, "Sven", 129L, "Pudge"));
  }

  private void summary(String team, String result, long balance, int kills) {
    summaries.add(
        OpeningSummaryDomain.builder()
            .id(nextId++)
            .matchId(1L)
            .team(team)
            .result(result)
            .balance(balance)
            .kills(kills)
            .build());
  }

  private RunePickupDomain rune(long slot, String type, int seconds, Integer gold, boolean mid) {
    RunePickupDomain row =
        RunePickupDomain.builder()
            .id(nextId++)
            .matchId(1L)
            .playerSlot(slot)
            .team(slot >= 128 ? "DIRE" : "RADIANT")
            .runeIndex((int) nextId)
            .runeType(type)
            .seconds(seconds)
            .gold(gold)
            .midLane(mid)
            .build();
    pickups.add(row);
    return row;
  }

  private ReplayReportInput input(Long... focus) {
    return new ReplayReportInput(1L, null, Set.of(focus), Map.of(), 3);
  }

  @Test
  void isOffByDefaultUntilThePropertyIsTrue() {
    SectionSwitch sectionSwitch = new SectionSwitch(properties, List.of(section));
    assertThat(section.name()).isEqualTo("opening");
    assertThat(section.defaultEnabled()).isFalse();
    assertThat(sectionSwitch.isEnabled(section)).isFalse();

    ReplayReportProperties.SectionToggle on = new ReplayReportProperties.SectionToggle();
    on.setEnabled(true);
    properties.setSections(Map.of("opening", on));

    assertThat(sectionSwitch.isEnabled(section)).isTrue();
  }

  @Test
  void tellsWhoWonByHowMuchTheBountyRunesAndTheMidLanePowerAndWaterRunes() {
    summary("RADIANT", "WON", 1200, 3);
    summary("DIRE", "LOST", -1200, 1);
    rune(0, "BOUNTY", 0, 100, false);
    rune(128, "BOUNTY", 2, 100, false);
    rune(1, "BOUNTY", 180, 130, false);
    rune(1, "WATER", 120, null, true);
    rune(129, "DOUBLE_DAMAGE", 560, null, true);
    rune(129, "HASTE", 600, null, false);
    rune(1, "WATER", 240, null, true);
    rune(0, "ARCANE", 700, null, true);
    rune(0, "SHIELD", 601, null, true);
    rune(0, "REGENERATION", 130, null, true);

    List<String> lines = section.lines(input());

    assertThat(lines)
        .containsExactly(
            "**Opening and runes**",
            "Radiant won the opening by 1.2k gold and XP; kills 3 to 1.",
            "Radiant took 2 bounty runes worth 230 gold in total, the first was Axe at 0:00; Dire"
                + " took 1 bounty rune worth 100 gold in total, the first was Sven at 0:02.",
            "In the mid lane in the first 10 minutes: Lion took water runes at 2:00 and 4:00; Pudge"
                + " took a double damage rune at 9:20.");
  }

  @Test
  void aTieOnGoldAndXpIsBrokenByKills() {
    summary("DIRE", "WON", 0, 4);
    summary("RADIANT", "LOST", 0, 2);

    assertThat(section.lines(input()).get(1))
        .isEqualTo("Dire won the opening on kills, 4 to 2, after gold and XP were level.");
  }

  @Test
  void aDrawSaysEverythingWasLevel() {
    summary("DIRE", "DRAW", 0, 2);
    summary("RADIANT", "DRAW", 0, 2);

    assertThat(section.lines(input()))
        .containsExactly(
            "**Opening and runes**", "The opening was level: gold, XP and kills were level.");
  }

  @Test
  void marksYourTeamWhereItIsNamed() {
    summary("RADIANT", "WON", 300, 1);
    summary("DIRE", "LOST", -300, 0);
    rune(0, "BOUNTY", 0, 100, false);

    List<String> lines = section.lines(input(0L, 1L));

    assertThat(lines.get(1)).startsWith("Radiant (your team) won the opening by 300 gold and XP");
    assertThat(lines.get(2)).contains("Radiant (your team) took 1");
    assertThat(section.lines(input(128L)).get(1)).startsWith("Radiant won");
  }

  @Test
  void withoutRuneRowsOnlyTheResultIsPrinted() {
    summary("RADIANT", "WON", 300, 1);
    summary("DIRE", "LOST", -300, 0);

    assertThat(section.lines(input())).hasSize(2);
  }

  @Test
  void aBountyRuneWithoutGoldDoesNotClaimAnAmountAndNoMidLineWithoutMidRunes() {
    summary("RADIANT", "WON", 300, 1);
    rune(0, "BOUNTY", 0, null, false);
    rune(0, "BOUNTY", 180, 130, false);

    List<String> lines = section.lines(input());

    assertThat(lines).hasSize(3);
    assertThat(lines.get(1)).isEqualTo("Radiant won the opening by 300 gold and XP.");
    assertThat(lines.get(2)).isEqualTo("Radiant took 2 bounty runes, the first was Axe at 0:00.");
  }

  @Test
  void printsNothingWithoutAnOpeningAndDoesNotThrowOnNullData() {
    rune(0, "BOUNTY", 0, 100, false);
    assertThat(section.lines(input())).isEmpty();
    when(summaryRepo.findByMatchId(1L)).thenReturn(null);
    assertThat(section.lines(input())).isEmpty();

    summaries.add(OpeningSummaryDomain.builder().build());
    summaries.add(null);
    pickups.clear();
    pickups.add(RunePickupDomain.builder().runeType("BOUNTY").build());
    pickups.add(RunePickupDomain.builder().runeType("HASTE").midLane(true).build());
    pickups.add(null);
    when(summaryRepo.findByMatchId(1L)).thenReturn(summaries);

    List<String> lines = section.lines(new ReplayReportInput(1L, null, null, null, 3));

    assertThat(lines).isEmpty();
  }

  @Test
  void theOrderDoesNotDependOnTheRepositoryOrder() {
    summary("RADIANT", "WON", 300, 1);
    rune(1, "BOUNTY", 0, 100, false);
    rune(0, "BOUNTY", 0, 100, false);
    List<RunePickupDomain> reversed = new ArrayList<>(pickups);
    Collections.reverse(reversed);
    when(pickupRepo.findByMatchId(1L)).thenReturn(reversed);

    assertThat(section.lines(input()).get(2)).contains("the first was Axe at 0:00");
  }

  @Test
  void cutsLinesAtTheConfiguredLengthOnAWordBoundary() {
    properties.setMaxOpeningLineLength(60);
    summary("RADIANT", "WON", 300, 1);
    summary("DIRE", "LOST", -300, 0);
    rune(0, "BOUNTY", 0, 100, false);

    List<String> lines = section.lines(input());

    assertThat(lines.get(2)).hasSizeLessThanOrEqualTo(60).endsWith("...");
  }

  @Test
  void aLostRowAloneNamesTheOtherTeamAsTheWinner() {
    summary("DIRE", "LOST", -500, 2);

    assertThat(section.lines(input(0L)))
        .containsExactly(
            "**Opening and runes**", "Radiant (your team) won the opening by 500 gold and XP.");
  }

  @Test
  void aLevelBalanceWithEqualOrUnknownKillsIsLevelNotWonOnKills() {
    summary("RADIANT", "WON", 0, 2);
    summary("DIRE", "LOST", 0, 2);
    assertThat(section.lines(input()).get(1)).startsWith("The opening was level");

    summaries.clear();
    summary("RADIANT", "WON", 0, 2);
    summaries.get(0).setKills(null);
    assertThat(section.lines(input()).get(1)).isEqualTo("The opening was level in gold and XP.");
  }

  @Test
  void theEnemyKillsAreThoseOfTheOppositeTeamWhateverTheRowOrder() {
    summary("DIRE", "LOST", -300, 5);
    summary("RADIANT", "WON", 300, 1);

    assertThat(section.lines(input()).get(1)).endsWith("; kills 1 to 5.");
  }

  @Test
  void midLaneRunesAreOnlyThoseOfTheFirstConfiguredMinutes() {
    properties.setOpeningRuneMinutes(5);
    summary("RADIANT", "WON", 300, 1);
    rune(0, "WATER", 300, null, true);
    rune(0, "WATER", 301, null, true);

    assertThat(section.lines(input()).get(2))
        .isEqualTo("In the mid lane in the first 5 minutes: Axe took a water rune at 5:00.");
  }

  @Test
  void validatesTheRuneMinutes() {
    for (int bad : new int[] {0, 31}) {
      properties.setOpeningRuneMinutes(bad);
      assertThatThrownBy(properties::validate).hasMessageContaining("opening-rune-minutes");
    }
    properties.setOpeningRuneMinutes(30);
    properties.validate();
  }

  @Test
  void validatesTheLineLength() {
    for (int bad : new int[] {59, 601}) {
      properties.setMaxOpeningLineLength(bad);
      assertThatThrownBy(properties::validate).hasMessageContaining("max-opening-line-length");
    }
    properties.setMaxOpeningLineLength(600);
    properties.validate();
  }
}
