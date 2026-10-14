package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.PlayerRoleProfileDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.model.TeamCompositionDomain;
import com.ako.dbuff.dao.repo.PlayerRoleProfileRepo;
import com.ako.dbuff.dao.repo.TeamCompositionRepo;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompositionSectionTest {

  private final PlayerRoleProfileRepo profileRepo = mock(PlayerRoleProfileRepo.class);
  private final TeamCompositionRepo compositionRepo = mock(TeamCompositionRepo.class);
  private final ReplayReportProperties properties = new ReplayReportProperties();
  private final CompositionSection section =
      new CompositionSection(profileRepo, compositionRepo, properties);

  private static PlayerRoleProfileDomain profile(long slot, String stage, int role) {
    return PlayerRoleProfileDomain.builder().playerSlot(slot).stage(stage).role(role).build();
  }

  private static TeamCompositionDomain composition(String team, String stage, String summary) {
    return TeamCompositionDomain.builder().team(team).stage(stage).summary(summary).build();
  }

  private ReplayReportInput input(Map<Long, String> names, int maxFindings) {
    return new ReplayReportInput(1L, null, names.keySet(), names, maxFindings);
  }

  @Test
  void anAbilityDraftShowsIntentFinalAndBothVerdicts() {
    when(profileRepo.findByMatchId(1L))
        .thenReturn(List.of(profile(0, "INTENT", 1), profile(0, "FINAL", 5)));
    when(compositionRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                composition("RADIANT", "FINAL", "no initiator. Unknown: disable"),
                composition("RADIANT", "PICK", "3 carries; no frontline"),
                composition("DIRE", "PICK", "no disable")));

    List<String> lines = section.lines(input(Map.of(0L, "Anton"), 3));

    assertThat(lines)
        .containsExactly(
            "**Roles and composition**",
            "Anton was meant to play carry and played hard support",
            "Radiant at the draft: 3 carries; no frontline",
            "Radiant in the game: no initiator. Unknown: disable");
  }

  @Test
  void anAllPickShowsTheFinalRoleAndFinalVerdictOnly() {
    when(profileRepo.findByMatchId(1L)).thenReturn(List.of(profile(129, "FINAL", 3)));
    when(compositionRepo.findByMatchId(1L))
        .thenReturn(List.of(composition("DIRE", "FINAL", "no gaps found")));

    List<String> lines = section.lines(input(Map.of(129L, "Anton"), 3));

    assertThat(lines)
        .containsExactly(
            "**Roles and composition**", "Anton played offlane", "Dire in the game: no gaps found");
  }

  @Test
  void aRoleKeptFromTheDraftIsSaidSo() {
    when(profileRepo.findByMatchId(1L))
        .thenReturn(List.of(profile(0, "INTENT", 2), profile(0, "FINAL", 2)));
    when(compositionRepo.findByMatchId(1L)).thenReturn(List.of());

    assertThat(section.lines(input(Map.of(0L, "Anton"), 3)))
        .containsExactly("**Roles and composition**", "Anton was meant to play and played mid");
  }

  @Test
  void onlyTheTeamsOfTheTrackedPlayersAreShown() {
    when(profileRepo.findByMatchId(1L)).thenReturn(List.of(profile(0, "FINAL", 1)));
    when(compositionRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                composition("RADIANT", "FINAL", "mine"), composition("DIRE", "FINAL", "theirs")));

    assertThat(section.lines(input(Map.of(0L, "Anton"), 3)))
        .containsExactly(
            "**Roles and composition**", "Anton played carry", "Radiant in the game: mine");
  }

  @Test
  void playerLinesAreCappedAndTheRestIsCounted() {
    Map<Long, String> names = new LinkedHashMap<>();
    List<PlayerRoleProfileDomain> profiles = new java.util.ArrayList<>();
    for (long slot = 0; slot < 4; slot++) {
      names.put(slot, "P" + slot);
      profiles.add(profile(slot, "FINAL", (int) slot + 1));
    }
    when(profileRepo.findByMatchId(1L)).thenReturn(profiles);
    when(compositionRepo.findByMatchId(1L)).thenReturn(List.of());

    List<String> lines = section.lines(input(names, 2));

    assertThat(lines)
        .containsExactly(
            "**Roles and composition**",
            "P0 played carry",
            "P1 played mid",
            "and 2 more tracked players");
  }

  @Test
  void aLongVerdictLineIsCutToTheConfiguredLength() {
    properties.setMaxCompositionLineLength(30);
    when(profileRepo.findByMatchId(1L)).thenReturn(List.of());
    when(compositionRepo.findByMatchId(1L))
        .thenReturn(List.of(composition("RADIANT", "FINAL", "x".repeat(100))));

    List<String> lines = section.lines(input(Map.of(0L, "Anton"), 3));

    assertThat(lines).hasSize(2);
    assertThat(lines.get(1)).hasSize(30).endsWith("...");
  }

  @Test
  void isEmptyWhenNothingIsStored() {
    when(profileRepo.findByMatchId(1L)).thenReturn(List.of());
    when(compositionRepo.findByMatchId(1L)).thenReturn(List.of());

    assertThat(section.lines(input(Map.of(0L, "Anton"), 3))).isEmpty();
  }

  @Test
  void sitsAfterTheDraftAtStandardDetailForEveryMode() {
    assertThat(section.minimumDetail()).isEqualTo(ReportDetail.STANDARD);
    assertThat(section.order()).isGreaterThan(new DraftSection(null, null, null).order());
    assertThat(section.supports(com.ako.dbuff.service.replay.mode.TestModes.ALL_PICK)).isTrue();
    assertThat(section.supports(com.ako.dbuff.service.replay.mode.TestModes.ABILITY_DRAFT))
        .isTrue();
  }
}
