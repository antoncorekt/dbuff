package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.PlayerPhaseSummaryDomain;
import com.ako.dbuff.dao.repo.PlayerPhaseSummaryRepo;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MovementSectionTest {

  private final PlayerPhaseSummaryRepo repo = mock(PlayerPhaseSummaryRepo.class);
  private final MovementSection section = new MovementSection(repo);

  @Test
  void lines_sayWhereTheTimeWentAndHowManyFightsWereJoined() {
    when(repo.findByMatchId(1L))
        .thenReturn(
            List.of(
                PlayerPhaseSummaryDomain.builder()
                    .playerSlot(0L)
                    .phase("MID")
                    .startSeconds(720)
                    .endSeconds(1500)
                    .deadSeconds(60)
                    .teleportingSeconds(0)
                    .inBaseSeconds(40)
                    .jungleFarmSeconds(480)
                    .laneFarmSeconds(100)
                    .pushingSeconds(0)
                    .travelingSeconds(100)
                    .fightsAvailable(7)
                    .fightsJoined(3)
                    .goldPerMinute(612.4)
                    .build()));

    List<String> lines =
        section.lines(new ReplayReportInput(1L, null, Set.of(0L), Map.of(0L, "Anton"), 3));

    assertThat(lines)
        .containsExactly(
            "**Movement**",
            "Anton 12-25 min: 62% farming the jungle, 13% farming a lane, joined 3 of 7 fights, 612"
                + " gold a minute");
  }

  @Test
  void lines_areEmptyWithoutSummaries() {
    when(repo.findByMatchId(1L)).thenReturn(List.of());

    assertThat(section.lines(new ReplayReportInput(1L, null, Set.of(0L), Map.of(0L, "Anton"), 3)))
        .isEmpty();
  }
}
