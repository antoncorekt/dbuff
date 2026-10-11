package com.ako.dbuff.service.replay;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReplayPlayerMinuteDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.repo.ReplayPlayerMinuteRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerTrackRepo;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({ReplayTrackExtractor.class, ReplayMinuteExtractor.class})
class ReplaySampleExtractorsTest {

  private static final MatchDomain MATCH = match();

  @Autowired private ReplayTrackExtractor trackExtractor;
  @Autowired private ReplayMinuteExtractor minuteExtractor;
  @Autowired private ReplayPlayerTrackRepo trackRepo;
  @Autowired private ReplayPlayerMinuteRepo minuteRepo;

  private List<ReplayPlayerFact> players;

  @BeforeEach
  void roster() {
    players =
        List.of(new ReplayPlayerFact(0, 2, 0L, "Sniper"), new ReplayPlayerFact(10, 3, 0L, "Sven"));
  }

  @Test
  void trackExtractor_storesEachPlayersArraysOnOpenDotaSlotsAndReplacesAnEarlierRun() {
    trackExtractor.extract(facts(), MATCH);
    trackExtractor.extract(facts(), MATCH);

    List<ReplayPlayerTrackDomain> tracks = trackRepo.findAll();
    assertThat(tracks).hasSize(2);
    ReplayPlayerTrackDomain dire =
        tracks.stream().filter(t -> t.getPlayerSlot() == 128L).findFirst().orElseThrow();
    assertThat(dire.getStartSeconds()).isEqualTo(-90);
    assertThat(dire.getStepSeconds()).isEqualTo(5);
    assertThat(dire.getSampleCount()).isEqualTo(3);
    assertThat(dire.getX()).containsExactly(-7000, 100, 6500);
    assertThat(dire.getMana()).containsExactly(0, 400, 240);
    assertThat(dire.getLevel()).containsExactly(0, 1, 2);
    assertThat(dire.getFlags()).containsExactly(0, 1, 1);
  }

  @Test
  void minuteExtractor_storesCountersAndLeavesAMissingFieldNull() {
    minuteExtractor.extract(facts(), MATCH);

    List<ReplayPlayerMinuteDomain> rows = minuteRepo.findAll();
    assertThat(rows).hasSize(2);
    ReplayPlayerMinuteDomain radiant =
        rows.stream().filter(r -> r.getPlayerSlot() == 0L).findFirst().orElseThrow();
    assertThat(radiant.getGameMinute()).isEqualTo(5);
    assertThat(radiant.getKills()).isEqualTo(3L);
    assertThat(radiant.getHeroKillGold()).isEqualTo(900L);
    assertThat(radiant.getPowerRunes()).isNull();
  }

  @Test
  void bothExtractors_storeNothingForAReplayWithoutSamples() {
    ReplayFacts none =
        ReplayFacts.builder().clockStartTick(100).gameStartTime(1.0).players(players).build();

    trackExtractor.extract(none, MATCH);
    minuteExtractor.extract(none, MATCH);

    assertThat(trackRepo.findAll()).isEmpty();
    assertThat(minuteRepo.findAll()).isEmpty();
  }

  @Test
  void deleteByMatchId_removesTheRowsOfThatMatch() {
    trackExtractor.extract(facts(), MATCH);
    minuteExtractor.extract(facts(), MATCH);

    trackExtractor.deleteByMatchId(MATCH.getId());
    minuteExtractor.deleteByMatchId(MATCH.getId());

    assertThat(trackRepo.findAll()).isEmpty();
    assertThat(minuteRepo.findAll()).isEmpty();
  }

  private ReplayFacts facts() {
    SampleFacts samples =
        new SampleFacts(
            -90,
            5,
            List.of(
                new SampleFacts.PlayerSeries(
                    0,
                    new int[] {-7000, 0, 6000},
                    new int[] {-6500, 0, 5000},
                    new int[] {0, 100, 80},
                    new int[] {0, 100, 70},
                    new int[] {0, 300, 210},
                    new int[] {0, 1, 2},
                    new int[] {0, 1, 1}),
                new SampleFacts.PlayerSeries(
                    10,
                    new int[] {-7000, 100, 6500},
                    new int[] {7000, 100, 6000},
                    new int[] {0, 100, 90},
                    new int[] {0, 100, 60},
                    new int[] {0, 400, 240},
                    new int[] {0, 1, 2},
                    new int[] {0, 1, 1})),
            List.of(minute(0, 5, 3, 900), minute(10, 5, 1, 0)));
    return ReplayFacts.builder()
        .clockStartTick(100)
        .gameStartTime(1.0)
        .players(players)
        .samples(samples)
        .build();
  }

  private static SampleFacts.MinuteSnapshot minute(
      int playerId, int gameMinute, long kills, long heroKillGold) {
    long[] values = new long[MinuteStat.values().length];
    java.util.Arrays.fill(values, 0);
    values[MinuteStat.KILLS.ordinal()] = kills;
    values[MinuteStat.HERO_KILL_GOLD.ordinal()] = heroKillGold;
    values[MinuteStat.POWER_RUNES.ordinal()] = MinuteStat.MISSING;
    return new SampleFacts.MinuteSnapshot(playerId, gameMinute, values);
  }

  private static MatchDomain match() {
    MatchDomain match = new MatchDomain();
    match.setId(9L);
    return match;
  }
}
