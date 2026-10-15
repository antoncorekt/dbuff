package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DraftSectionTest {

  private final AbilityDraftPickRepo repo = mock(AbilityDraftPickRepo.class);
  private final DraftSection section =
      new DraftSection(repo, new AbilityDraftProfile(), Prettify::ability);

  private AbilityDraftPickDomain pick(int order, long slot, String ability) {
    return AbilityDraftPickDomain.builder()
        .pickOrder(order)
        .teamPickIndex((order + 1) / 2)
        .playerSlot(slot)
        .abilityName(ability)
        .build();
  }

  @Test
  void lines_listWhatEachTrackedPlayerDraftedAndWhere() {
    when(repo.findByMatchId(1L))
        .thenReturn(
            List.of(
                pick(12, 0, "sven_gods_strength"),
                pick(3, 0, "faceless_void_chronosphere"),
                pick(22, 0, "sven_storm_bolt"),
                pick(1, 128, "axe_culling_blade")));

    List<String> lines =
        section.lines(new ReplayReportInput(1L, null, Set.of(0L), Map.of(0L, "Anton"), 3));

    assertThat(lines)
        .containsExactly(
            "**Draft**",
            "Anton drafted Faceless Void Chronosphere (pick 3), Sven Gods Strength (pick 12), Sven"
                + " Storm Bolt (pick 22)");
  }

  @Test
  void lines_nameACrossHeroPickByItsAbilityNotItsSourceHero() {
    when(repo.findByMatchId(1L))
        .thenReturn(
            List.of(pick(3, 0, "terrorblade_metamorphosis"), pick(12, 0, "sven_gods_strength")));
    DraftSection real = new DraftSection(repo, new AbilityDraftProfile(), TestLabels.labels());

    assertThat(real.lines(new ReplayReportInput(1L, null, Set.of(0L), Map.of(0L, "Anton"), 3)))
        .containsExactly(
            "**Draft**", "Anton drafted Metamorphosis (pick 3), Gods Strength (pick 12)");
  }

  @Test
  void lines_areEmptyWithoutADraft() {
    when(repo.findByMatchId(1L)).thenReturn(List.of());

    assertThat(section.lines(new ReplayReportInput(1L, null, Set.of(0L), Map.of(0L, "Anton"), 3)))
        .isEmpty();
  }

  @Test
  void lines_printTheOverallPickAsStoredFromOne() {
    when(repo.findByMatchId(1L))
        .thenReturn(List.of(pick(1, 0, "sven_gods_strength"), pick(40, 0, "sven_storm_bolt")));

    assertThat(section.lines(new ReplayReportInput(1L, null, Set.of(0L), Map.of(0L, "Anton"), 3)))
        .containsExactly(
            "**Draft**", "Anton drafted Sven Gods Strength (pick 1), Sven Storm Bolt (pick 40)");
  }

  @Test
  void lines_fallBackToTheTeamPickWhenTheOverallOrderIsNullAndSortThoseLast() {
    AbilityDraftPickDomain unordered =
        AbilityDraftPickDomain.builder()
            .teamPickIndex(2)
            .playerSlot(0L)
            .abilityName("sven_storm_bolt")
            .build();
    AbilityDraftPickDomain bare =
        AbilityDraftPickDomain.builder().playerSlot(0L).abilityName("sven_great_cleave").build();
    when(repo.findByMatchId(1L))
        .thenReturn(List.of(unordered, bare, pick(5, 0, "sven_gods_strength")));

    assertThat(section.lines(new ReplayReportInput(1L, null, Set.of(0L), Map.of(0L, "Anton"), 3)))
        .containsExactly(
            "**Draft**",
            "Anton drafted Sven Gods Strength (pick 5), Sven Storm Bolt (team pick 2), Sven Great"
                + " Cleave");
  }

  @Test
  void view_countsTheTrackedPlayersAndKeepsPlainTextAsItWas() {
    when(repo.findByMatchId(1L))
        .thenReturn(List.of(pick(12, 0, "sven_gods_strength"), pick(3, 0, "sven_storm_bolt")));
    ReplayReportInput in = new ReplayReportInput(1L, null, Set.of(0L), Map.of(0L, "Anton"), 3);

    SectionView view = section.view(in).orElseThrow();

    ViewRendering.assertWithinLimits(view);
    assertThat(view.title()).isEqualTo("Draft");
    assertThat(view.verdict()).isEqualTo("Ability picks of 1 tracked player");
    assertThat(view.accent()).isEqualTo(Accent.NEUTRAL);
    assertThat(view.bullets())
        .containsExactly("Anton drafted Sven Storm Bolt (pick 3), Sven Gods Strength (pick 12)");
    assertThat(String.join("\n", section.lines(in)))
        .isEqualTo(
            "**Draft**\nAnton drafted Sven Storm Bolt (pick 3), Sven Gods Strength (pick 12)");
    assertThat(new PlainViewRenderer().lines(view)).isEqualTo(section.lines(in));
  }

  @Test
  void view_isEmptyWithoutADraft() {
    when(repo.findByMatchId(1L)).thenReturn(List.of());

    assertThat(section.view(new ReplayReportInput(1L, null, Set.of(0L), Map.of(0L, "Anton"), 3)))
        .isEmpty();
  }
}
