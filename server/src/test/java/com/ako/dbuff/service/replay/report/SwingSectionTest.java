package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.GameSpikeDomain;
import com.ako.dbuff.dao.model.GameSpikeFactorDomain;
import com.ako.dbuff.dao.repo.GameSpikeFactorRepo;
import com.ako.dbuff.dao.repo.GameSpikeRepo;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SwingSectionTest {

  private final GameSpikeRepo spikeRepo = mock(GameSpikeRepo.class);
  private final GameSpikeFactorRepo factorRepo = mock(GameSpikeFactorRepo.class);
  private final SwingSection section = new SwingSection(spikeRepo, factorRepo);

  private ReplayReportInput input() {
    return new ReplayReportInput(1L, null, Set.of(), Map.of(), 2);
  }

  private GameSpikeDomain spike(int index, int start, long gold, String cause, boolean flip) {
    return GameSpikeDomain.builder()
        .spikeIndex(index)
        .startSeconds(start)
        .endSeconds(start + 180)
        .benefitingTeam("DIRE")
        .deltaGold(gold)
        .directionChange(flip)
        .primaryCause(cause)
        .build();
  }

  @Test
  void lines_listTheBiggestSwingsInTimeOrderWithTheirCauses() {
    when(spikeRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                spike(0, 1680, 7400, "FIGHT_WIN", true),
                spike(1, 600, 1600, "CARRY_FARM", false),
                spike(2, 1200, 3000, "OBJECTIVE", false),
                spike(3, 300, 9000, "OTHER", false)));
    when(factorRepo.findByMatchId(1L))
        .thenReturn(
            List.of(
                GameSpikeFactorDomain.builder()
                    .spikeIndex(0)
                    .factorType("SOURCE")
                    .ref("HERO_KILLS")
                    .sharePercent(62.0)
                    .build(),
                GameSpikeFactorDomain.builder()
                    .spikeIndex(0)
                    .factorType("SOURCE")
                    .ref("ROSHAN")
                    .sharePercent(21.0)
                    .build()));

    List<String> lines = section.lines(input());

    assertThat(lines).hasSize(3);
    assertThat(lines.get(0)).isEqualTo("**Swings**");
    assertThat(lines.get(1))
        .startsWith("20:00-23:00: Dire gained 3.0k net worth")
        .contains("buildings");
    assertThat(lines.get(2))
        .startsWith("28:00-31:00: Dire gained 7.4k net worth and took the lead")
        .contains("a won fight (kills 62%, Roshan 21%)");
  }

  @Test
  void lines_areEmptyWhenNoSwingHasACause() {
    when(spikeRepo.findByMatchId(1L)).thenReturn(List.of(spike(0, 300, 9000, "OTHER", false)));

    assertThat(section.lines(input())).isEmpty();
  }
}
