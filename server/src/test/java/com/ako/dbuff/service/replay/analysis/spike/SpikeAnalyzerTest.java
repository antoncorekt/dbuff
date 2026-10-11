package com.ako.dbuff.service.replay.analysis.spike;

import com.ako.dbuff.dao.model.BuildingKillContextDomain;
import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.GameSpikeDomain;
import com.ako.dbuff.dao.model.GameSpikeFactorDomain;
import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import com.ako.dbuff.dao.model.ReplayPlayerMinuteDomain;
import com.ako.dbuff.dao.repo.BuildingKillContextRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.GameSpikeFactorRepo;
import com.ako.dbuff.dao.repo.GameSpikeRepo;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerMinuteRepo;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({SpikeAnalyzer.class, ReplayAnalysisProperties.class})
class SpikeAnalyzerTest {

  private static final long MATCH = 17L;

  @Autowired private SpikeAnalyzer analyzer;
  @Autowired private PlayerTimelineRepo timelineRepo;
  @Autowired private ReplayPlayerMinuteRepo minuteRepo;
  @Autowired private FightRepo fightRepo;
  @Autowired private BuildingKillContextRepo buildingRepo;
  @Autowired private GameSpikeRepo spikeRepo;
  @Autowired private GameSpikeFactorRepo factorRepo;

  @Test
  void analyze_findsTheSwingAndNamesTheFightWonAsItsCause_andReplacesAnEarlierRun() {
    stored(true);

    analyzer.analyze(MATCH);
    analyzer.analyze(MATCH);

    List<GameSpikeDomain> spikes = spikeRepo.findByMatchId(MATCH);
    assertThat(spikes).hasSize(1);
    GameSpikeDomain spike = spikes.getFirst();
    assertThat(spike.getBenefitingTeam()).isEqualTo("RADIANT");
    assertThat(spike.getStartSeconds()).isEqualTo(180);
    assertThat(spike.getEndSeconds()).isEqualTo(240);
    assertThat(spike.getDeltaGold()).isEqualTo(3000);
    assertThat(spike.getPrimaryCause()).isEqualTo("FIGHT_WIN");
    assertThat(spike.getConfidence()).isEqualTo(1.0);
    assertThat(spike.getDirectionChange()).isFalse();
    List<GameSpikeFactorDomain> factors = factorRepo.findByMatchId(MATCH);
    assertThat(factors).extracting("factorType").contains("SOURCE", "FIGHT", "OBJECTIVE");
    assertThat(factors)
        .filteredOn(f -> f.getFactorType().equals("SOURCE"))
        .extracting("ref", "goldValue")
        .containsExactly(org.assertj.core.groups.Tuple.tuple("HERO_KILLS", 3000L));
  }

  @Test
  void analyze_callsTheCauseOtherWhenNoReplayCountersExist() {
    stored(false);

    analyzer.analyze(MATCH);

    GameSpikeDomain spike = spikeRepo.findByMatchId(MATCH).getFirst();
    assertThat(spike.getPrimaryCause()).isEqualTo("OTHER");
    assertThat(spike.getConfidence()).isZero();
  }

  @Test
  void analyze_findsNothingInAnEvenGame() {
    for (int minute = 0; minute < 8; minute++) {
      for (int p = 0; p < 10; p++) {
        timelineRepo.save(timeline(p, minute, 5000));
      }
    }
    timelineRepo.flush();

    analyzer.analyze(MATCH);

    assertThat(spikeRepo.findAll()).isEmpty();
  }

  @Test
  void deleteByMatchId_removesSpikesAndFactors() {
    stored(true);
    analyzer.analyze(MATCH);

    analyzer.deleteByMatchId(MATCH);

    assertThat(spikeRepo.findAll()).isEmpty();
    assertThat(factorRepo.findAll()).isEmpty();
  }

  /** Even until minute 3, then Radiant gains 3000 in a minute from kills it made in a won fight. */
  private void stored(boolean withCounters) {
    for (int minute = 0; minute < 8; minute++) {
      for (int p = 0; p < 10; p++) {
        long worth = minute < 4 || p >= 5 ? 5000 : 5600;
        timelineRepo.save(timeline(p, minute, worth));
        if (withCounters) {
          minuteRepo.save(
              ReplayPlayerMinuteDomain.builder()
                  .matchId(MATCH)
                  .playerSlot(p < 5 ? (long) p : 128L + p - 5)
                  .gameMinute(minute)
                  .heroKillGold(minute < 4 || p >= 5 ? 0L : 600L)
                  .build());
        }
      }
    }
    fightRepo.save(
        FightDomain.builder()
            .matchId(MATCH)
            .fightIndex(0)
            .type("TEAMFIGHT")
            .startSeconds(200.0)
            .endSeconds(230.0)
            .winner("RADIANT")
            .radiantDeaths(1)
            .direDeaths(4)
            .build());
    buildingRepo.save(
        BuildingKillContextDomain.builder()
            .matchId(MATCH)
            .buildingName("npc_dota_badguys_tower1_mid")
            .seconds(235.0)
            .kind("TOWER")
            .ownerTeam("DIRE")
            .build());
    timelineRepo.flush();
  }

  private PlayerTimelineDomain timeline(int p, int minute, long worth) {
    return PlayerTimelineDomain.builder()
        .matchId(MATCH)
        .playerSlot(p < 5 ? (long) p : 128L + p - 5)
        .gameMinute(minute)
        .networth(worth)
        .build();
  }
}
