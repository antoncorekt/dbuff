package com.ako.dbuff.service.replay.analysis.team;

import com.ako.dbuff.dao.model.GameStateSegmentDomain;
import com.ako.dbuff.dao.model.PlayerTimelineDomain;
import com.ako.dbuff.dao.repo.GameStateSegmentRepo;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
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
@Import({TeamStateAnalyzer.class, ReplayAnalysisProperties.class})
class TeamStateAnalyzerTest {

  private static final long MATCH = 14L;

  @Autowired private TeamStateAnalyzer analyzer;
  @Autowired private PlayerTimelineRepo timelineRepo;
  @Autowired private GameStateSegmentRepo segmentRepo;

  @Test
  void analyze_storesMirroredSegmentsForBothTeams_andReplacesAnEarlierRun() {
    stored(10, true);

    analyzer.analyze(MATCH);
    analyzer.analyze(MATCH);

    List<GameStateSegmentDomain> rows = segmentRepo.findByMatchId(MATCH);
    assertThat(rows)
        .filteredOn(r -> r.getTeam().equals("RADIANT"))
        .extracting("state")
        .containsExactly("EVEN", "AHEAD");
    assertThat(rows)
        .filteredOn(r -> r.getTeam().equals("DIRE"))
        .extracting("state")
        .containsExactly("EVEN", "BEHIND");
    GameStateSegmentDomain ahead =
        rows.stream().filter(r -> r.getState().equals("AHEAD")).findFirst().orElseThrow();
    assertThat(ahead.getStartSeconds()).isEqualTo(300);
    assertThat(ahead.getPeakLeadPercent()).isEqualTo(20.0);
  }

  @Test
  void analyze_storesNothingWithoutANetWorthForEveryPlayer() {
    stored(10, false);

    analyzer.analyze(MATCH);

    assertThat(segmentRepo.findAll()).isEmpty();
  }

  @Test
  void deleteByMatchId_removesTheSegments() {
    stored(10, true);
    analyzer.analyze(MATCH);

    analyzer.deleteByMatchId(MATCH);

    assertThat(segmentRepo.findAll()).isEmpty();
  }

  /** Even for five minutes, then Radiant 20 percent ahead (6000 against 4000 per player). */
  private void stored(int minutes, boolean withNetWorth) {
    for (int minute = 0; minute < minutes; minute++) {
      for (int p = 0; p < 10; p++) {
        boolean radiant = p < 5;
        long worth = minute < 5 ? 5000 : radiant ? 6000 : 4000;
        timelineRepo.save(
            PlayerTimelineDomain.builder()
                .matchId(MATCH)
                .playerSlot(radiant ? (long) p : 128L + p - 5)
                .gameMinute(minute)
                .networth(withNetWorth ? worth : null)
                .build());
      }
    }
    timelineRepo.flush();
  }
}
