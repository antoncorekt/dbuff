package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.FightAbilityUseDomain;
import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.repo.FightAbilityUseRepo;
import com.ako.dbuff.dao.repo.FightPlayerRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.service.replay.analysis.role.SkillFunctions;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FightSectionTest {

  private final FightRepo fightRepo = mock(FightRepo.class);
  private final FightPlayerRepo partRepo = mock(FightPlayerRepo.class);
  private final FightAbilityUseRepo abilityRepo = mock(FightAbilityUseRepo.class);
  private final SkillFunctions skills = mock(SkillFunctions.class);
  private final FightSection section =
      new FightSection(fightRepo, partRepo, abilityRepo, Prettify::ability, skills);

  private ReplayReportInput input() {
    return new ReplayReportInput(1L, null, Set.of(128L), Map.of(128L, "Anton"), 3);
  }

  @Test
  void lines_sayWhyATrackedPlayerMissedABigFightAndWhichAbilityHeLeftUnused() {
    when(fightRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                FightDomain.builder()
                    .fightIndex(0)
                    .type("TEAMFIGHT")
                    .startSeconds(1390.0)
                    .radiantDeaths(4)
                    .direDeaths(1)
                    .winner("RADIANT")
                    .build(),
                FightDomain.builder()
                    .fightIndex(1)
                    .type("SKIRMISH")
                    .startSeconds(500.0)
                    .radiantDeaths(1)
                    .direDeaths(0)
                    .winner("RADIANT")
                    .build()));
    when(partRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                FightPlayerDomain.builder()
                    .fightIndex(0)
                    .playerSlot(128L)
                    .presence("ABSENT_FARMING")
                    .absenceReason("NO_TP")
                    .build(),
                FightPlayerDomain.builder()
                    .fightIndex(1)
                    .playerSlot(128L)
                    .presence("ABSENT_FAR")
                    .absenceReason("NO_TP")
                    .build()));
    List<FightAbilityUseDomain> uses = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      uses.add(
          FightAbilityUseDomain.builder()
              .fightIndex(i)
              .playerSlot(128L)
              .abilityName("faceless_void_chronosphere")
              .nonCastReason(i < 2 ? "READY_NOT_CAST" : null)
              .build());
    }
    when(abilityRepo.findByMatchId(1L)).thenReturn(uses);

    List<String> lines = section.lines(input());

    assertThat(lines)
        .containsExactly(
            "**Fights**",
            "23:10 teamfight (lost, deaths 1 to 4): Anton was farming away from it, with no"
                + " teleport.",
            "Anton had Faceless Void Chronosphere ready and did not cast it in 2 of 3 fights.");
  }

  @Test
  void lines_leaveOutAnItemAndAHiddenAbilityStoredAsReadyNotCast() {
    when(fightRepo.findByMatchId(1L)).thenReturn(List.of());
    when(partRepo.findByMatchId(1L)).thenReturn(List.of());
    when(skills.notCastable("item_magic_stick")).thenReturn(true);
    when(skills.notCastable("morphling_hidden")).thenReturn(true);
    List<FightAbilityUseDomain> uses = new ArrayList<>();
    for (String ability : List.of("item_magic_stick", "morphling_hidden")) {
      for (int i = 0; i < 3; i++) {
        uses.add(
            FightAbilityUseDomain.builder()
                .fightIndex(i)
                .playerSlot(128L)
                .abilityName(ability)
                .nonCastReason("READY_NOT_CAST")
                .build());
      }
    }
    when(abilityRepo.findByMatchId(1L)).thenReturn(uses);

    assertThat(String.join("\n", section.lines(input()))).doesNotContain("ready and did not cast");
  }

  @Test
  void lines_leaveOutAPassiveAbilityStoredAsReadyNotCast() {
    when(fightRepo.findByMatchId(1L)).thenReturn(List.of());
    when(partRepo.findByMatchId(1L)).thenReturn(List.of());
    when(skills.notCastable("axe_counter_helix")).thenReturn(true);
    List<FightAbilityUseDomain> uses = new ArrayList<>();
    for (String ability : List.of("axe_counter_helix", "axe_culling_blade")) {
      for (int i = 0; i < 3; i++) {
        uses.add(
            FightAbilityUseDomain.builder()
                .fightIndex(i)
                .playerSlot(128L)
                .abilityName(ability)
                .nonCastReason("READY_NOT_CAST")
                .build());
      }
    }
    when(abilityRepo.findByMatchId(1L)).thenReturn(uses);

    List<String> lines = section.lines(input());

    assertThat(String.join("\n", lines))
        .contains("Axe Culling Blade")
        .doesNotContain("Counter Helix");
  }

  @Test
  void lines_areEmptyWhenNothingWentWrong() {
    when(fightRepo.findByMatchId(1L)).thenReturn(List.of());
    when(partRepo.findByMatchId(1L)).thenReturn(List.of());
    when(abilityRepo.findByMatchId(1L)).thenReturn(List.of());

    assertThat(section.lines(input())).isEmpty();
  }

  private void stubBigFightAndUnusedUltimate() {
    when(fightRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                FightDomain.builder()
                    .fightIndex(0)
                    .type("TEAMFIGHT")
                    .startSeconds(1390.0)
                    .radiantDeaths(4)
                    .direDeaths(1)
                    .winner("RADIANT")
                    .build()));
    when(partRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                FightPlayerDomain.builder()
                    .fightIndex(0)
                    .playerSlot(128L)
                    .presence("ABSENT_FARMING")
                    .absenceReason("NO_TP")
                    .build()));
    List<FightAbilityUseDomain> uses = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      uses.add(
          FightAbilityUseDomain.builder()
              .fightIndex(i)
              .playerSlot(128L)
              .abilityName("faceless_void_chronosphere")
              .nonCastReason(i < 2 ? "READY_NOT_CAST" : null)
              .build());
    }
    when(abilityRepo.findByMatchId(1L)).thenReturn(uses);
  }

  @Test
  void view_countsMissedFightsAndUnusedAbilitiesAndKeepsPlainTextAsItWas() {
    stubBigFightAndUnusedUltimate();

    SectionView view = section.view(input()).orElseThrow();

    ViewRendering.assertWithinLimits(view);
    assertThat(view.title()).isEqualTo("Fights");
    assertThat(view.verdict())
        .isEqualTo(
            "Anton missed 1 big fight (1 lost) and left a ready ability unused in several"
                + " fights");
    assertThat(view.accent()).isEqualTo(Accent.LOSS);
    assertThat(view.bullets())
        .containsExactly(
            "23:10 teamfight (lost, deaths 1 to 4): Anton was farming away from it, with no"
                + " teleport.",
            "Anton had Faceless Void Chronosphere ready and did not cast it in 2 of 3 fights.");
    assertThat(String.join("\n", section.lines(input())))
        .isEqualTo(
            "**Fights**\n"
                + "23:10 teamfight (lost, deaths 1 to 4): Anton was farming away from it, with no"
                + " teleport.\n"
                + "Anton had Faceless Void Chronosphere ready and did not cast it in 2 of 3"
                + " fights.");
    assertThat(new PlainViewRenderer().lines(view)).isEqualTo(section.lines(input()));
  }

  @Test
  void view_isEmptyWhenNothingWentWrong() {
    when(fightRepo.findByMatchId(1L)).thenReturn(List.of());
    when(partRepo.findByMatchId(1L)).thenReturn(List.of());
    when(abilityRepo.findByMatchId(1L)).thenReturn(List.of());

    assertThat(section.view(input())).isEmpty();
  }

  @Test
  void view_countsEveryMissedFightNotOnlyTheListedOnesAndStaysGreyWhenTheyWereWon() {
    List<FightDomain> fights = new ArrayList<>();
    List<FightPlayerDomain> parts = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      fights.add(
          FightDomain.builder()
              .fightIndex(i)
              .type("TEAMFIGHT")
              .startSeconds(100.0 * (i + 1))
              .radiantDeaths(1)
              .direDeaths(2)
              .winner("RADIANT")
              .build());
      for (long slot : new long[] {0L, 129L}) {
        parts.add(
            FightPlayerDomain.builder()
                .fightIndex(i)
                .playerSlot(slot)
                .presence("ABSENT_FAR")
                .absenceReason("NO_TP")
                .build());
      }
    }
    when(fightRepo.findByMatchId(1L)).thenReturn(fights);
    when(partRepo.findByMatchId(1L)).thenReturn(parts);
    when(abilityRepo.findByMatchId(1L)).thenReturn(List.of());
    ReplayReportInput both =
        new ReplayReportInput(
            1L,
            null,
            Set.of(0L, 129L),
            new java.util.LinkedHashMap<>(Map.of(0L, "A", 129L, "B")),
            2);

    SectionView view = section.view(both).orElseThrow();

    assertThat(view.bullets()).hasSize(4);
    assertThat(view.verdict()).isEqualTo("Tracked players missed 8 big fights (4 lost)");
    assertThat(view.accent()).isEqualTo(Accent.LOSS);
  }

  @Test
  void view_staysGreyWhenTheMissedFightsWereWon() {
    when(fightRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                FightDomain.builder()
                    .fightIndex(0)
                    .type("TEAMFIGHT")
                    .startSeconds(100.0)
                    .radiantDeaths(2)
                    .direDeaths(1)
                    .winner("DIRE")
                    .build()));
    when(partRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                FightPlayerDomain.builder()
                    .fightIndex(0)
                    .playerSlot(128L)
                    .presence("ABSENT_FAR")
                    .absenceReason("NO_TP")
                    .build()));
    when(abilityRepo.findByMatchId(1L)).thenReturn(List.of());

    SectionView view = section.view(input()).orElseThrow();

    assertThat(view.verdict()).isEqualTo("Anton missed 1 big fight");
    assertThat(view.accent()).isEqualTo(Accent.NEUTRAL);
  }
}
