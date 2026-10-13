package com.ako.dbuff.service.replay.analysis.importance;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.FightImportanceDomain;
import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import com.ako.dbuff.dao.model.TeamTimelineDomain;
import com.ako.dbuff.dao.repo.FightImportanceRepo;
import com.ako.dbuff.dao.repo.FightRepo;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import com.ako.dbuff.dao.repo.TeamTimelineRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@ActiveProfiles("test")
@Import({
  ImportantFightAnalyzer.class,
  AdvantageSeriesReader.class,
  ImportantFightRule.class,
  ImportantFightProperties.class
})
class ImportantFightAnalyzerTest {
  private static final long MATCH = 81L;
  private static final long[] SLOTS = {0, 1, 2, 3, 4, 128, 129, 130, 131, 132};

  @Autowired private ImportantFightAnalyzer analyzer;
  @Autowired private FightImportanceRepo importanceRepo;
  @Autowired private FightRepo fightRepo;
  @Autowired private TeamTimelineRepo teamRepo;
  @Autowired private PlayerTimelineRepo playerRepo;

  private void fight(int index, String winner, double start, double end) {
    fightRepo.save(
        FightDomain.builder()
            .matchId(MATCH)
            .fightIndex(index)
            .winner(winner)
            .startSeconds(start)
            .endSeconds(end)
            .build());
  }

  private void timeline(long[] goldAdvantage, Long networth, Long gold) {
    for (int minute = 0; minute < goldAdvantage.length; minute++) {
      teamRepo.save(
          TeamTimelineDomain.builder()
              .matchId(MATCH)
              .gameMinute(minute)
              .radiantGoldAdv(goldAdvantage[minute])
              .radiantXpAdv(0L)
              .build());
      for (long slot : SLOTS) {
        playerRepo.save(
            PlayerTimelineDomain.builder()
                .matchId(MATCH)
                .playerSlot(slot)
                .gameMinute(minute)
                .networth(networth)
                .gold(gold)
                .build());
      }
    }
  }

  @Test
  void everyDecidedFightGetsARow_drawsAndUnknownWinnersGetNone() {
    timeline(new long[] {0, 0, 0, 0, 3000, 3000, 3000, 3000}, 4000L, null);
    fight(0, "RADIANT", 150, 170);
    fight(1, "DRAW", 200, 210);
    fight(2, null, 220, 230);
    fight(3, "DIRE", 300, 320);

    analyzer.analyze(MATCH);

    assertThat(importanceRepo.findByMatchId(MATCH))
        .extracting(
            FightImportanceDomain::getFightIndex,
            FightImportanceDomain::getWinningTeam,
            FightImportanceDomain::getGoldChange,
            FightImportanceDomain::getImportant)
        .containsExactlyInAnyOrder(tuple(0, "RADIANT", 3000L, true), tuple(3, "DIRE", 0L, false));
  }

  @Test
  void theShareIsOfTheWinnersNetWorth_andGoldStandsInWhenNetWorthIsMissing() {
    timeline(new long[] {0, 0, 0, 800, 800, 800}, null, 1000L);
    fight(0, "RADIANT", 120, 130);

    analyzer.analyze(MATCH);

    FightImportanceDomain row = importanceRepo.findByMatchId(MATCH).get(0);
    assertThat(row.getNetWorth()).isEqualTo(5000L);
    assertThat(row.getSharePercent()).isEqualTo(16.0);
    assertThat(row.getImportant()).isTrue();
  }

  @Test
  void aMatchWithoutAnAdvantageSeriesStoresNothing_andRunningAgainReplacesTheRows() {
    fight(0, "RADIANT", 120, 130);

    analyzer.analyze(MATCH);
    assertThat(importanceRepo.findByMatchId(MATCH)).isEmpty();

    timeline(new long[] {0, 0, 0, 3000, 3000}, 4000L, null);
    analyzer.analyze(MATCH);
    analyzer.analyze(MATCH);
    assertThat(importanceRepo.findByMatchId(MATCH)).hasSize(1);

    analyzer.deleteByMatchId(MATCH);
    assertThat(importanceRepo.findByMatchId(MATCH)).isEmpty();
  }

  @Test
  void readsTheFightsAndTheFightLossRows() {
    assertThat(analyzer.requires()).containsExactlyInAnyOrder("fights", "fight-loss");
    assertThat(analyzer.name()).isEqualTo("important-fights");
  }
}
