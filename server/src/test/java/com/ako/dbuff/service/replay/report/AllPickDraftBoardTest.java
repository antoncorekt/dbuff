package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.DraftEventDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.repo.DraftEventRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.service.replay.mode.AbilityDraftProfile;
import com.ako.dbuff.service.replay.mode.TestModes;
import com.ako.dbuff.service.replay.report.view.PlainViewRenderer;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AllPickDraftBoardTest {
  private final DraftEventRepo eventRepo = mock(DraftEventRepo.class);
  private final PlayerGameStatisticRepo statRepo = mock(PlayerGameStatisticRepo.class);
  private final ReportHeroNames heroNames = mock(ReportHeroNames.class);
  private final AllPickDraftBoard board = new AllPickDraftBoard(eventRepo, statRepo, heroNames);
  private final DraftBoardSection section =
      new DraftBoardSection(
          List.of(
              board,
              new AbilityDraftBoard(
                  null, null, null, new AbilityDraftProfile(), Prettify::ability, heroNames)),
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

  private static PlayerMatchStatisticDomain player(long slot, long hero) {
    PlayerMatchStatisticDomain stat = new PlayerMatchStatisticDomain();
    stat.setPlayerSlot(slot);
    stat.setHeroId(hero);
    return stat;
  }

  private ReplayReportInput input() {
    when(heroNames.heroName(anyLong())).thenAnswer(i -> "Hero" + i.getArgument(0));
    return new ReplayReportInput(1L, null, Set.of(), Map.of(130L, "Anton"), 3);
  }

  private void draft() {
    when(eventRepo.findByMatchIdOrderByDraftOrder(1L))
        .thenReturn(
            List.of(
                event(0, true, 11, 0),
                event(1, true, 22, 1),
                event(2, true, 33, 1),
                event(3, false, 44, 0)));
    when(statRepo.findAllByMatchId(1L)).thenReturn(List.of(player(0, 11), player(130, 33)));
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
  void section_supportsAllPickAndAbilityDraftAndUsesTheBoardWithData() {
    draft();

    assertThat(section.supports(TestModes.ALL_PICK)).isTrue();
    assertThat(section.supports(TestModes.ABILITY_DRAFT)).isTrue();
    assertThat(section.lines(input())).contains("**Hero picks**");
    assertThat(section.defaultEnabled()).isFalse();
  }
}
