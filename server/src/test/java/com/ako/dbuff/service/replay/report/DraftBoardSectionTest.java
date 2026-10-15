package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.AbilityDraftPickDomain;
import com.ako.dbuff.dao.model.AbilityDraftPoolDomain;
import com.ako.dbuff.dao.model.HeroDraftPickDomain;
import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import com.ako.dbuff.dao.repo.AbilityDraftPoolRepo;
import com.ako.dbuff.dao.repo.HeroDraftPickRepo;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.TestModes;
import com.ako.dbuff.service.replay.report.view.ComponentsViewRenderer;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.ReportMessage;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DraftBoardSectionTest {
  private static final long SVEN = 18;
  private static final long SNIPER = 35;
  private static final long MEEPO = 82;

  private final HeroDraftPickRepo heroRepo = mock(HeroDraftPickRepo.class);
  private final AbilityDraftPickRepo skillRepo = mock(AbilityDraftPickRepo.class);
  private final AbilityDraftPoolRepo poolRepo = mock(AbilityDraftPoolRepo.class);
  private final ReportHeroNames heroNames = mock(ReportHeroNames.class);
  private final PlainViewRenderer plain = new PlainViewRenderer();
  private final DraftBoardSection section =
      new DraftBoardSection(
          List.of(
              new AbilityDraftBoard(
                  heroRepo,
                  skillRepo,
                  poolRepo,
                  new AbilityDraftProfile(),
                  Prettify::ability,
                  heroNames)),
          plain);

  private ReplayReportInput input(Set<Long> focus, Map<Long, String> names) {
    when(heroNames.heroName(SVEN)).thenReturn("Sven");
    when(heroNames.heroName(SNIPER)).thenReturn("Sniper");
    when(heroNames.heroName(MEEPO)).thenReturn("Meepo");
    return new ReplayReportInput(1L, null, focus, names, 3);
  }

  private static HeroDraftPickDomain hero(long id, Long slot, Integer turn, Double clock) {
    return HeroDraftPickDomain.builder()
        .matchId(1L)
        .heroId(id)
        .playerSlot(slot)
        .turnNumber(turn)
        .pickTick(clock == null ? null : (int) (clock * 30))
        .clockSeconds(clock)
        .build();
  }

  private static AbilityDraftPickDomain skill(int order, int turn, long slot, String name) {
    return AbilityDraftPickDomain.builder()
        .pickOrder(order)
        .turnNumber(turn)
        .playerSlot(slot)
        .abilityName(name)
        .build();
  }

  private void draft() {
    when(heroRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                hero(MEEPO, null, null, null),
                hero(SVEN, 130L, 15, -403.9),
                hero(SNIPER, 0L, 20, -372.5)));
    when(skillRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                skill(2, 2, 130, "sven_storm_bolt"),
                skill(1, 1, 0, "sniper_take_aim"),
                skill(3, 3, 0, "sniper_headshot")));
    when(poolRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                AbilityDraftPoolDomain.builder()
                    .poolIndex(0)
                    .abilityName("meepo_poof")
                    .pickedBySlot(null)
                    .build(),
                AbilityDraftPoolDomain.builder()
                    .poolIndex(1)
                    .abilityName("sniper_take_aim")
                    .pickedBySlot(0L)
                    .build()));
  }

  @Test
  void view_listsHeroPicksInTurnOrderTrackedPlayersByNameAndOthersByHeroOnly() {
    draft();

    SectionView view = section.view(input(Set.of(130L), Map.of(130L, "Anton"))).orElseThrow();

    assertThat(view.title()).isEqualTo("Draft board");
    assertThat(view.bullets().getFirst().lines())
        .containsExactly(
            "**Hero picks**",
            "15. Sven, picked by Anton (Dire) at -6:44",
            "20. Sniper, picked by Radiant at -6:12");
  }

  @Test
  void view_groupsSkillsByPickerWithTheTrackedTeamFirst() {
    draft();

    SectionView view = section.view(input(Set.of(130L), Map.of(130L, "Anton"))).orElseThrow();

    assertThat(view.bullets().get(1).lines())
        .containsExactly(
            "**Skill picks**",
            "Anton (Sven): Sven Storm Bolt (2)",
            "Sniper: Sniper Take Aim (1), Sniper Headshot (3)");
  }

  @Test
  void view_putsRadiantFirstWhenNobodyIsTracked() {
    draft();

    SectionView view = section.view(input(Set.of(), Map.of())).orElseThrow();

    assertThat(view.bullets().get(1).lines().toList().get(1)).startsWith("Sniper:");
  }

  @Test
  void view_listsWhatWasLeftOver() {
    draft();

    SectionView view = section.view(input(Set.of(), Map.of())).orElseThrow();

    assertThat(view.bullets().get(2).lines())
        .containsExactly("**Left over**", "Heroes: Meepo", "Skills: Meepo Poof");
  }

  @Test
  void view_isEmptyWithoutHeroRows() {
    when(heroRepo.findByMatchId(1L)).thenReturn(List.of());

    assertThat(section.view(input(Set.of(), Map.of()))).isEmpty();
    assertThat(section.lines(input(Set.of(), Map.of()))).isEmpty();
  }

  @Test
  void lines_areOnePlainLinePerRowAfterTheTitle() {
    draft();

    List<String> lines = section.lines(input(Set.of(130L), Map.of(130L, "Anton")));

    assertThat(lines).first().isEqualTo("**Draft board**");
    assertThat(lines).contains("15. Sven, picked by Anton (Dire) at -6:44", "Heroes: Meepo");
    assertThat(lines).allMatch(line -> !line.contains("\n"));
  }

  @Test
  void render_fitsTheSectionIntoComponentsCardsAndPlainText() {
    draft();
    SectionView view = section.view(input(Set.of(130L), Map.of(130L, "Anton"))).orElseThrow();

    List<ReportMessage> cards = new ComponentsViewRenderer(plain).render(view);
    List<ReportMessage> text = plain.render(view);

    assertThat(cards).hasSize(1).first().isInstanceOf(ReportMessage.Components.class);
    assertThat(((ReportMessage.Components) cards.getFirst()).fallback())
        .contains("Hero picks", "Skill picks", "Left over");
    assertThat(text).hasSize(1);
    assertThat(((ReportMessage.Plain) text.getFirst()).text()).contains("Hero picks", "Left over");
  }

  @Test
  void render_pagesAFullDraftWithoutCuttingARow() {
    List<HeroDraftPickDomain> heroes = new ArrayList<>();
    List<AbilityDraftPickDomain> skills = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      long slot = i < 5 ? i : 128 + i - 5;
      heroes.add(hero(SVEN, slot, i + 1, -400.0 + i));
      for (int j = 0; j < 4; j++) {
        skills.add(
            skill(i * 4 + j + 1, i * 4 + j + 11, slot, "sven_ability_with_a_long_name_" + j));
      }
    }
    when(heroRepo.findByMatchId(1L)).thenReturn(heroes);
    when(skillRepo.findByMatchId(1L)).thenReturn(skills);
    when(poolRepo.findByMatchId(1L)).thenReturn(List.of());
    SectionView view = section.view(input(Set.of(), Map.of())).orElseThrow();

    List<ReportMessage> cards = new ComponentsViewRenderer(plain).render(view);

    assertThat(cards).isNotEmpty();
    assertThat(cards).allMatch(c -> c instanceof ReportMessage.Components);
    assertThat(cards.stream().map(c -> ((ReportMessage.Components) c).fallback()))
        .noneMatch(text -> text.contains("…"));
  }

  @Test
  void view_showsASkillWithoutANameAndAPickerWhoseSkillsAreAllUnnamed() {
    draft();
    when(skillRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                AbilityDraftPickDomain.builder()
                    .pickOrder(1)
                    .turnNumber(1)
                    .playerSlot(0L)
                    .abilityId(5156L)
                    .build()));

    SectionView view = section.view(input(Set.of(), Map.of())).orElseThrow();

    assertThat(view.bullets().get(1).lines().toList())
        .containsExactly("**Skill picks**", "Sniper: Skill 5156 (1)");
  }

  @Test
  void view_showsNoTurnForASkillWithoutOneAndSaysTheOrderIsUnknown() {
    draft();
    when(skillRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                AbilityDraftPickDomain.builder()
                    .pickOrder(7)
                    .playerSlot(0L)
                    .abilityName("sniper_take_aim")
                    .build()));

    SectionView view = section.view(input(Set.of(), Map.of())).orElseThrow();

    assertThat(view.bullets().get(1).lines().toList())
        .containsExactly("**Skill picks**", "Sniper: Sniper Take Aim");
    assertThat(view.footnote()).contains("order is not known");
  }

  @Test
  void view_leavesTheUnknownOrderNoteOutWhenEveryTurnIsKnown() {
    draft();

    assertThat(section.view(input(Set.of(), Map.of())).orElseThrow().footnote())
        .doesNotContain("not known");
  }

  @Test
  void supports_onlyAbilityDraft() {
    assertThat(section.supports(TestModes.ABILITY_DRAFT)).isTrue();
    assertThat(section.supports(TestModes.ALL_PICK)).isFalse();
  }

  @Test
  void isOffByDefaultAndSitsNextToTheDraftSection() {
    assertThat(section.defaultEnabled()).isFalse();
    assertThat(section.name()).isEqualTo("draft-board");
    assertThat(section.order()).isEqualTo(11);
  }
}
