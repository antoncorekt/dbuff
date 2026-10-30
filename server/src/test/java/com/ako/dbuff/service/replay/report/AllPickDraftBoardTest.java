package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.DraftEventDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.AbilityDraftPickRepo;
import com.ako.dbuff.dao.repo.AbilityDraftPoolRepo;
import com.ako.dbuff.dao.repo.DraftEventRepo;
import com.ako.dbuff.dao.repo.HeroDraftPickRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.TestModes;
import com.ako.dbuff.service.replay.report.chart.SkillIconImages;
import com.ako.dbuff.service.replay.report.chart.TableRenderer;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AllPickDraftBoardTest {
  private final DraftEventRepo eventRepo = mock(DraftEventRepo.class);
  private final PlayerGameStatisticRepo statRepo = mock(PlayerGameStatisticRepo.class);
  private final ReportHeroNames heroNames = mock(ReportHeroNames.class);
  private final AllPickDraftBoard board = new AllPickDraftBoard(eventRepo, statRepo, heroNames);
  private final HeroDraftPickRepo abilityHeroRepo = mock(HeroDraftPickRepo.class);
  private final DraftBoardSection section =
      new DraftBoardSection(
          List.of(
              board,
              new AbilityDraftBoard(
                  abilityHeroRepo,
                  mock(AbilityDraftPickRepo.class),
                  mock(AbilityDraftPoolRepo.class),
                  mock(PlayerGameStatisticRepo.class),
                  new AbilityDraftProfile(),
                  Prettify::ability,
                  heroNames,
                  mock(SkillIconImages.class),
                  mock(TableRenderer.class))),
          new PlainViewRenderer());

  private static DraftEventDomain event(int order, boolean pick, long hero, int team) {
    return DraftEventDomain.builder()
        .matchId(1L)
        .draftOrder(order)
        .pick(pick)
        .heroId(hero)
        .team(team)
        .build();
  }

  private record Player(Long getPlayerSlot, Long getHeroId)
      implements PlayerGameStatisticRepo.HeroSlot {}

  private static Player player(Long slot, Long hero) {
    return new Player(slot, hero);
  }

  private static DraftEventDomain raw(int order, Boolean pick, Integer team) {
    return DraftEventDomain.builder()
        .matchId(1L)
        .draftOrder(order)
        .pick(pick)
        .heroId(55L)
        .team(team)
        .build();
  }

  private static MatchDomain match(long mode) {
    MatchDomain match = new MatchDomain();
    match.setGameModeId(mode);
    return match;
  }

  private ReplayReportInput input() {
    when(heroNames.heroName(anyLong())).thenAnswer(i -> "Hero" + i.getArgument(0));
    return new ReplayReportInput(1L, match(22L), Set.of(), Map.of(130L, "Anton"), 3);
  }

  private void draft() {
    when(eventRepo.findByMatchIdOrderByDraftOrder(1L))
        .thenReturn(
            List.of(
                event(0, true, 11, 0),
                event(1, true, 22, 1),
                event(2, true, 33, 1),
                event(3, false, 44, 0)));
    when(statRepo.findHeroSlots(1L)).thenReturn(List.of(player(0L, 11L), player(130L, 33L)));
  }

  @Test
  void view_listsPicksInDraftOrderWithNumbersTeamsAndTrackedNames() {
    draft();

    SectionView view = board.view(input()).orElseThrow();

    assertThat(view.title()).isEqualTo("Draft board");
    assertThat(view.bullets().getFirst().lines())
        .containsExactly(
            "**Hero picks**",
            "1. Hero11, picked by Radiant",
            "2. Hero22, picked by Dire, not played",
            "3. Hero33, picked by Anton (Dire)");
  }

  @Test
  void view_listsBansWithTheirOverallNumber() {
    draft();

    SectionView view = board.view(input()).orElseThrow();

    assertThat(view.bullets().get(1).lines()).containsExactly("**Bans**", "4. Hero44");
  }

  @Test
  void view_isEmptyWhenOpenDotaGaveNoDraft() {
    when(eventRepo.findByMatchIdOrderByDraftOrder(1L)).thenReturn(List.of());

    assertThat(board.view(input())).isEmpty();
  }

  @Test
  void view_doesNotSayNotPlayedWhenTheStoredPlayersAreMissing() {
    draft();
    when(statRepo.findHeroSlots(1L)).thenReturn(List.of());

    assertThat(board.view(input()).orElseThrow().bullets().getFirst())
        .doesNotContain("not played")
        .contains("2. Hero22, picked by Dire");
  }

  @Test
  void view_doesNotSayNotPlayedWhenAStoredPlayerHasNoHero() {
    draft();
    when(statRepo.findHeroSlots(1L)).thenReturn(List.of(player(0L, 11L), player(130L, null)));

    assertThat(board.view(input()).orElseThrow().bullets().getFirst()).doesNotContain("not played");
  }

  @Test
  void view_takesTheSideFromThePlayersSlotWhenTheEventDisagrees() {
    when(eventRepo.findByMatchIdOrderByDraftOrder(1L)).thenReturn(List.of(event(0, true, 11, 1)));
    when(statRepo.findHeroSlots(1L)).thenReturn(List.of(player(0L, 11L)));

    assertThat(board.view(input()).orElseThrow().bullets().getFirst())
        .contains("1. Hero11, picked by Radiant");
  }

  @Test
  void view_skipsRowsWithNoPickFlagAndNamesNoSideWhenTheTeamIsMissing() {
    when(eventRepo.findByMatchIdOrderByDraftOrder(1L))
        .thenReturn(List.of(raw(0, null, 0), raw(1, true, null)));
    when(statRepo.findHeroSlots(1L)).thenReturn(List.of());

    SectionView view = board.view(input()).orElseThrow();

    assertThat(view.bullets()).hasSize(1);
    assertThat(view.bullets().getFirst().lines())
        .containsExactly("**Hero picks**", "2. Hero55, picked by an unknown side");
  }

  @Test
  void view_isEmptyWhenEveryRowIsUnusable() {
    when(eventRepo.findByMatchIdOrderByDraftOrder(1L)).thenReturn(List.of(raw(0, null, 0)));

    assertThat(board.view(input())).isEmpty();
  }

  @Test
  void section_usesTheBoardOfTheMatchModeAndNoOther() {
    draft();

    assertThat(section.supports(TestModes.ALL_PICK)).isTrue();
    assertThat(section.supports(TestModes.ABILITY_DRAFT)).isTrue();
    assertThat(section.lines(input())).contains("**Hero picks**", "1. Hero11, picked by Radiant");
    assertThat(section.defaultEnabled()).isFalse();
    verify(abilityHeroRepo, never()).findByMatchId(anyLong());

    ReplayReportInput adMatch = new ReplayReportInput(1L, match(18L), Set.of(), Map.of(), 3);
    assertThat(section.view(adMatch)).isEmpty();
    verify(abilityHeroRepo).findByMatchId(1L);
    verify(eventRepo, times(1)).findByMatchIdOrderByDraftOrder(1L);
  }

  @Test
  void section_isEmptyWhenTheModeOfTheMatchIsNotKnown() {
    draft();

    assertThat(section.view(new ReplayReportInput(1L, null, Set.of(), Map.of(), 3))).isEmpty();
  }
}
