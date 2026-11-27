package com.ako.dbuff.service.replay.analysis.defense;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.MissingDefenseItemDomain;
import com.ako.dbuff.dao.model.TeamDefenseItemDomain;
import com.ako.dbuff.dao.model.TeamDefensePointDomain;
import com.ako.dbuff.dao.repo.DefenseStatRepository;
import com.ako.dbuff.dao.repo.DefenseStatRepository.ItemSituation;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.MissingDefenseItemRepo;
import com.ako.dbuff.dao.repo.TeamDefenseItemRepo;
import com.ako.dbuff.dao.repo.TeamDefensePointRepo;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.StoredMatchModes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MissingDefenseItemAnalyzerTest {
  private static final long MATCH = 7L;
  private static final long START = 1_700_000_000L;

  private final TeamDefensePointRepo pointRepo = mock(TeamDefensePointRepo.class);
  private final TeamDefenseItemRepo itemRepo = mock(TeamDefenseItemRepo.class);
  private final MissingDefenseItemRepo missingRepo = mock(MissingDefenseItemRepo.class);
  private final DefenseStatRepository stats = mock(DefenseStatRepository.class);
  private final StoredMatchModes modes = mock(StoredMatchModes.class);
  private final MatchRepo matchRepo = mock(MatchRepo.class);
  private final MissingDefenseProperties properties = new MissingDefenseProperties();
  private MissingDefenseItemAnalyzer analyzer;

  private final List<TeamDefensePointDomain> points = new ArrayList<>();
  private final List<TeamDefenseItemDomain> items = new ArrayList<>();

  @BeforeEach
  void setUp() {
    GameModeProfile mode = mock(GameModeProfile.class);
    when(mode.gameModeId()).thenReturn(22L);
    when(modes.activeFor(MATCH)).thenReturn(Optional.of(mode));
    MatchDomain match = new MatchDomain();
    match.setStartTime(START);
    when(matchRepo.findById(MATCH)).thenReturn(Optional.of(match));
    when(pointRepo.findByMatchId(MATCH)).thenReturn(points);
    when(itemRepo.findByMatchId(MATCH)).thenReturn(items);
    when(stats.itemSituation(anyInt(), any(), any(), anyDouble(), anyLong(), anyLong(), anyLong()))
        .thenReturn(new ItemSituation(14, 9, 19, 5));
    analyzer =
        new MissingDefenseItemAnalyzer(
            pointRepo,
            itemRepo,
            missingRepo,
            stats,
            modes,
            matchRepo,
            properties,
            new MissingDefenseWording(properties));
  }

  private void point(String checkpoint, int minute, double mag, double phys) {
    points.add(
        TeamDefensePointDomain.builder()
            .matchId(MATCH)
            .team("RADIANT")
            .checkpoint(checkpoint)
            .minute(minute)
            .enemyMagShare(mag)
            .enemyPhysShare(phys)
            .build());
  }

  private void magicItem(int atSecond) {
    items.add(
        TeamDefenseItemDomain.builder()
            .matchId(MATCH)
            .team("RADIANT")
            .kind("MAGIC")
            .item("pipe")
            .atSecond(atSecond)
            .build());
  }

  @SuppressWarnings("unchecked")
  private List<MissingDefenseItemDomain> saved() {
    ArgumentCaptor<List<MissingDefenseItemDomain>> captor = ArgumentCaptor.forClass(List.class);
    verify(missingRepo).saveAll(captor.capture());
    return captor.getValue();
  }

  @Test
  void shareBelowTheBarGivesNoRow() {
    point("M20", 20, 0.54, 0.46);

    analyzer.analyze(MATCH);

    assertThat(saved()).isEmpty();
  }

  @Test
  void itemBoughtBeforeTheCheckpointGivesNoRow() {
    point("M20", 20, 0.7, 0.3);
    magicItem(19 * 60);

    analyzer.analyze(MATCH);

    assertThat(saved()).isEmpty();
  }

  @Test
  void itemBoughtAfterM20StillMissesM20ButNotM30() {
    point("M20", 20, 0.7, 0.3);
    point("M30", 30, 0.7, 0.3);
    magicItem(21 * 60);

    analyzer.analyze(MATCH);

    assertThat(saved())
        .extracting("checkpoint", "kind")
        .containsExactly(org.assertj.core.groups.Tuple.tuple("M20", "MAGIC"));
  }

  @Test
  void historyNumbersAreCopiedAndTheWindowIsTwelveMonthsBeforeTheMatch() {
    point("M20", 20, 0.62, 0.38);

    analyzer.analyze(MATCH);

    MissingDefenseItemDomain row = saved().get(0);
    assertThat(row)
        .extracting("withGames", "withWins", "withoutGames", "withoutWins", "analysisVersion")
        .containsExactly(14L, 9L, 19L, 5L, 1);
    assertThat(row.getEvidence()).contains("with one 9 of 14 won, without 5 of 19");
    long from = START - 366L * 24 * 3600;
    verify(stats)
        .itemSituation(
            eq(22),
            eq("M20"),
            eq("MAGIC"),
            eq(0.55),
            org.mockito.ArgumentMatchers.longThat(f -> f < START - 364L * 24 * 3600 && f >= from),
            eq(START),
            eq(MATCH));
  }

  @Test
  void otherCheckpointsAreIgnoredAndAnUnknownMatchWritesNothing() {
    point("M10", 10, 0.9, 0.1);
    point("END", 40, 0.9, 0.1);
    analyzer.analyze(MATCH);
    assertThat(saved()).isEmpty();

    when(matchRepo.findById(8L)).thenReturn(Optional.empty());
    analyzer.analyze(8L);
    verify(missingRepo, org.mockito.Mockito.times(1)).saveAll(any());
  }
}
