package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.service.match.report.MatchReportOrchestrator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpenDotaSectionTest {
  private final MatchReportOrchestrator orchestrator = mock(MatchReportOrchestrator.class);
  private final OpenDotaSection section = new OpenDotaSection(orchestrator);
  private final MatchDomain match = MatchDomain.builder().id(5L).build();
  private final DbufInstanceConfigDomain instance = new DbufInstanceConfigDomain();

  @Test
  void isNamedOpendotaAndOffByDefault() {
    assertThat(section.name()).isEqualTo("opendota");
    assertThat(section.defaultEnabled()).isFalse();
  }

  @Test
  void showsTheMessagesOfTheLegacyReportOfTheChannel() {
    when(orchestrator.legacyMessages(match, instance)).thenReturn(List.of("lanes...", "items..."));
    ReplayReportInput input =
        new ReplayReportInput(5L, match, Set.of(), java.util.Map.of(), 3).withInstance(instance);

    assertThat(section.lines(input)).containsExactly("lanes...", "items...");
  }

  @Test
  void saysNothingWithoutAChannel() {
    ReplayReportInput input = new ReplayReportInput(5L, match, Set.of(), java.util.Map.of(), 3);

    assertThat(section.lines(input)).isEmpty();
    verifyNoInteractions(orchestrator);
  }
}
