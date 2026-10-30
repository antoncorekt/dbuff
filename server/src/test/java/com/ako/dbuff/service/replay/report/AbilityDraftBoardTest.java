package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.model.AbilityDraftPoolDomain;
import com.ako.dbuff.dao.model.HeroDraftPickDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import com.ako.dbuff.dao.repo.AbilityDraftPoolRepo;
import com.ako.dbuff.dao.repo.HeroDraftPickRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.report.chart.SkillIconImages;
import com.ako.dbuff.service.replay.report.chart.TableRenderer;
import com.ako.dbuff.service.replay.report.chart.TableSpec;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbilityDraftBoardTest {
  private static final long[] SLOTS = {0, 1, 2, 3, 4, 128, 129, 130, 131, 132};

  private final HeroDraftPickRepo heroRepo = mock(HeroDraftPickRepo.class);
  private final AbilityDraftPickRepo skillRepo = mock(AbilityDraftPickRepo.class);
  private final AbilityDraftPoolRepo poolRepo = mock(AbilityDraftPoolRepo.class);
  private final PlayerGameStatisticRepo stats = mock(PlayerGameStatisticRepo.class);
  private final ReportHeroNames heroNames = mock(ReportHeroNames.class);
  private final SkillIconImages icons = mock(SkillIconImages.class);
  private final TableRenderer tables = mock(TableRenderer.class);
  private final AbilityDraftBoard board =
      new AbilityDraftBoard(
          heroRepo,
          skillRepo,
          poolRepo,
          stats,
          new AbilityDraftProfile(),
          Prettify::ability,
          heroNames,
          icons,
          tables);

  AbilityDraftBoardTest() {
    when(tables.render(any())).thenReturn(Optional.of(new byte[] {1}));
    when(icons.ability(any())).thenReturn(Optional.empty());
    when(icons.hero(anyLong())).thenReturn(Optional.empty());
    when(heroNames.heroName(anyLong())).thenAnswer(i -> "Hero" + i.getArgument(0));
    when(stats.findAllByMatchId(anyLong())).thenReturn(List.of());
    when(poolRepo.findByMatchId(1L)).thenReturn(List.of());
  }

  private static ReplayReportInput input(Map<Long, String> names) {
    return new ReplayReportInput(1L, null, names.keySet(), names, 3);
  }

  private void fullDraft(boolean withTurns) {
    List<HeroDraftPickDomain> heroes = new ArrayList<>();
    List<AbilityDraftPickDomain> skills = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      long slot = SLOTS[(i % 2 == 0 ? 0 : 5) + (i / 2) % 5];
      skills.add(
          AbilityDraftPickDomain.builder()
              .pickOrder(i + 1)
              .turnNumber(withTurns ? i + 1 : null)
              .playerSlot(slot)
              .abilityName("hero_skill_" + i)
              .build());
    }
    for (int i = 0; i < 10; i++) {
      heroes.add(
          HeroDraftPickDomain.builder()
              .matchId(1L)
              .heroId(100L + i)
              .playerSlot(SLOTS[i])
              .turnNumber(withTurns ? 41 + i : null)
              .build());
    }
    when(heroRepo.findByMatchId(1L)).thenReturn(heroes);
    when(skillRepo.findByMatchId(1L)).thenReturn(skills);
  }

  @Test
  void view_givesEveryTurnInOrderWithItsTeamAndPicker() {
    fullDraft(true);

    SectionView view = board.view(input(Map.of(128L, "Пастух"))).orElseThrow();

    List<String> lines = view.bullets().getFirst().lines().skip(1).toList();
    assertThat(lines).hasSize(50);
    assertThat(lines.getFirst()).isEqualTo("1. Radiant · Hero100: Hero Skill 0");
    assertThat(lines.get(1)).isEqualTo("2. Dire · Пастух (Hero105): Hero Skill 1");
    assertThat(lines.getLast()).startsWith("50. Dire · ");
    assertThat(lines).allMatch(l -> l.matches("\\d+\\. (Radiant|Dire) · .*: .*"));
    assertThat(view.footnote()).doesNotContain("not known");
  }

  @Test
  void picture_hasTwoColumnsOfTeamsAndMarksTheTrackedPlayer() {
    fullDraft(true);

    board.view(input(Map.of(128L, "Пастух"))).orElseThrow();

    ArgumentCaptor<TableSpec> spec = ArgumentCaptor.forClass(TableSpec.class);
    verify(tables).render(spec.capture());
    assertThat(spec.getValue().headers())
        .containsExactly("Radiant", "Picked by", "Dire", "Picked by");
    assertThat(spec.getValue().rows()).hasSize(25);
    assertThat(spec.getValue().rows().getFirst().get(0).note()).isEqualTo("#1");
    assertThat(spec.getValue().rows().getFirst().get(2).note()).isEqualTo("#2");
    assertThat(spec.getValue().rows().getFirst().get(3).text()).isEqualTo("Пастух (Hero105)");
    assertThat(spec.getValue().rows().getFirst().get(3).band()).isNotNull();
    assertThat(spec.getValue().rows().getFirst().get(1).band()).isNull();
    assertThat(spec.getValue().rows().size()).isLessThanOrEqualTo(TableRenderer.MAX_ROWS);
  }

  @Test
  void view_namesAnUntrackedPickerByHeroElseBySlot() {
    when(heroRepo.findByMatchId(1L))
        .thenReturn(List.of(HeroDraftPickDomain.builder().matchId(1L).heroId(7L).build()));
    when(skillRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                AbilityDraftPickDomain.builder()
                    .turnNumber(1)
                    .playerSlot(2L)
                    .abilityName("a_b")
                    .build()));

    SectionView view = board.view(input(Map.of())).orElseThrow();

    assertThat(view.bullets().getFirst().lines().toList())
        .contains("1. Radiant · Radiant player 3: A B");
  }

  @Test
  void view_takesThePlayersHeroFromTheMatchStatisticsLikeTheDraftSection() {
    fullDraft(true);
    when(stats.findAllByMatchId(1L))
        .thenReturn(
            List.of(PlayerMatchStatisticDomain.builder().playerSlot(0L).heroId(5L).build()));

    SectionView view = board.view(input(Map.of(0L, "Anton"))).orElseThrow();

    assertThat(view.bullets().getFirst().lines().toList())
        .contains("1. Radiant · Anton (Hero5): Hero Skill 0");
  }

  @Test
  void view_listsWhatWasLeftOverAfterTheTurns() {
    fullDraft(true);
    when(poolRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                AbilityDraftPoolDomain.builder().poolIndex(1).abilityName("x_y").build(),
                AbilityDraftPoolDomain.builder()
                    .poolIndex(2)
                    .abilityName("taken")
                    .pickedBySlot(0L)
                    .build()));
    when(heroRepo.findByMatchId(1L))
        .thenReturn(List.of(HeroDraftPickDomain.builder().matchId(1L).heroId(9L).build()));

    SectionView view = board.view(input(Map.of())).orElseThrow();

    assertThat(view.bullets().get(1).lines())
        .containsExactly("**Left over**", "Heroes: Hero9", "Skills: X Y");
    ArgumentCaptor<TableSpec> spec = ArgumentCaptor.forClass(TableSpec.class);
    verify(tables).render(spec.capture());
    assertThat(spec.getValue().rows().get(spec.getValue().rows().size() - 2).get(0).text())
        .isEqualTo("Left over");
    assertThat(spec.getValue().rows().getLast().get(0).text()).isEqualTo("Hero9");
  }

  @Test
  void view_fallsBackToPickOrderWithoutTurnsAndSaysSo() {
    fullDraft(false);

    SectionView view = board.view(input(Map.of())).orElseThrow();

    assertThat(view.bullets().getFirst().lines().skip(1).findFirst().orElseThrow())
        .startsWith("1. Radiant · ");
    assertThat(view.footnote()).contains("not known");
  }

  @Test
  void view_numbersNoPickInventedWhenOnlySomeHaveATurn() {
    fullDraft(true);
    List<AbilityDraftPickDomain> skills = new ArrayList<>(skillRepo.findByMatchId(1L));
    skills.set(
        0,
        AbilityDraftPickDomain.builder()
            .pickOrder(1)
            .playerSlot(0L)
            .abilityName("hero_skill_0")
            .build());
    when(skillRepo.findByMatchId(1L)).thenReturn(skills);

    List<String> lines =
        board.view(input(Map.of())).orElseThrow().bullets().getFirst().lines().skip(1).toList();

    assertThat(lines.getLast()).isEqualTo("Radiant · Hero100: Hero Skill 0");
    assertThat(lines.stream().filter(l -> l.startsWith("1. "))).isEmpty();
  }

  @Test
  void view_keepsTheTextWhenOneSideHasTooManyTurnsForThePicture() {
    List<AbilityDraftPickDomain> skills = new ArrayList<>();
    for (int i = 0; i < TableRenderer.MAX_ROWS + 1; i++) {
      skills.add(
          AbilityDraftPickDomain.builder()
              .turnNumber(i + 1)
              .playerSlot(0L)
              .abilityName("hero_skill_" + i)
              .build());
    }
    when(heroRepo.findByMatchId(1L))
        .thenReturn(List.of(HeroDraftPickDomain.builder().matchId(1L).heroId(7L).build()));
    when(skillRepo.findByMatchId(1L)).thenReturn(skills);
    when(tables.render(any())).thenReturn(Optional.empty());

    SectionView view = board.view(input(Map.of())).orElseThrow();

    assertThat(view.image()).isNull();
    assertThat(view.bullets().getFirst().lines().skip(1)).hasSize(TableRenderer.MAX_ROWS + 1);
    assertThat(view.bullets()).contains(AbilityDraftBoard.NOT_DRAWN);
  }

  @Test
  void view_keepsTheTextAndSaysSoWhenThePictureFails() {
    fullDraft(true);
    when(tables.render(any())).thenReturn(Optional.empty());

    SectionView view = board.view(input(Map.of())).orElseThrow();

    assertThat(view.image()).isNull();
    assertThat(view.bullets()).contains(AbilityDraftBoard.NOT_DRAWN);
    assertThat(view.bullets().getFirst()).contains("1. Radiant");
  }

  @Test
  void view_isRenderedOncePerInput() {
    fullDraft(true);
    ReplayReportInput in = input(Map.of(0L, "A"));

    board.view(in);
    board.view(in);

    verify(tables, times(1)).render(any());
  }
}
