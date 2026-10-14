package com.ako.dbuff.service.replay.analysis.spike;

import com.ako.dbuff.dao.model.BuildingKillContextDomain;
import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightPlayerDomain;
import com.ako.dbuff.dao.model.GameSpikeDomain;
import com.ako.dbuff.dao.model.GameSpikeFactorDomain;
import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import com.ako.dbuff.dao.model.ReplayPlayerMinuteDomain;
import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.dao.repo.BuildingKillContextRepo;
import com.ako.dbuff.dao.repo.FightPlayerRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.GameSpikeFactorRepo;
import com.ako.dbuff.dao.repo.GameSpikeRepo;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerMinuteRepo;
import com.ako.dbuff.dao.repo.ReplayStateChangeRepo;
import com.ako.dbuff.service.constant.data.ItemConstant;
import com.ako.dbuff.service.replay.analysis.ReplayAnalysisProperties;
import com.ako.dbuff.service.replay.analysis.item.ItemKits;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({
  SpikeAnalyzer.class,
  ReplayAnalysisProperties.class,
  KeyItemFinder.class,
  SpikeProperties.class,
  SpikeAnalyzerTest.Kits.class
})
class SpikeAnalyzerTest {

  private static final long MATCH = 17L;

  @TestConfiguration
  static class Kits {
    @Bean
    ItemKits kits() {
      return key ->
          "black_king_bar".equals(key)
              ? Optional.of(ItemConstant.builder().cost(3975L).build())
              : Optional.empty();
    }
  }

  @Autowired private FightPlayerRepo fightPlayerRepo;
  @Autowired private ReplayStateChangeRepo stateRepo;
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
  void analyze_writesOneItemRowForTheKeyItem_andRewritesItOnRerun() {
    stored(true);
    fightPlayerRepo.save(
        FightPlayerDomain.builder()
            .matchId(MATCH)
            .fightIndex(0)
            .playerSlot(3L)
            .presence("ACTIVE")
            .build());
    stateRepo.save(item(3L, "item_black_king_bar", 120.0));
    stateRepo.save(item(4L, "item_black_king_bar", 125.0));
    stateRepo.save(item(3L, "item_unknown", 130.0));

    analyzer.analyze(MATCH);
    analyzer.analyze(MATCH);

    assertThat(factorRepo.findByMatchId(MATCH))
        .filteredOn(f -> f.getFactorType().equals("ITEM"))
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.getRef()).isEqualTo("black_king_bar");
              assertThat(f.getPlayerSlot()).isEqualTo(3L);
              assertThat(f.getGoldValue()).isEqualTo(3975L);
              assertThat(f.getSharePercent()).isNull();
              assertThat(f.getDetail()).isEqualTo("120");
            });
    assertThat(spikeRepo.findByMatchId(MATCH).getFirst().getAnalysisVersion()).isEqualTo(2);
  }

  @Test
  void analyze_writesNoItemRowWhenTheOwnerWasNotActive() {
    stored(true);
    stateRepo.save(item(3L, "item_black_king_bar", 120.0));

    analyzer.analyze(MATCH);

    assertThat(factorRepo.findByMatchId(MATCH)).extracting("factorType").doesNotContain("ITEM");
  }

  private ReplayStateChangeDomain item(long slot, String name, double seconds) {
    return ReplayStateChangeDomain.builder()
        .matchId(MATCH)
        .playerSlot(slot)
        .name(name)
        .gameSeconds(seconds)
        .kind("ITEM_ADDED")
        .build();
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
