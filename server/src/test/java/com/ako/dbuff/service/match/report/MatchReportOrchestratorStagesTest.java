package com.ako.dbuff.service.match.report;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.AbilityRepo;
import com.ako.dbuff.dao.repo.ItemRepository;
import com.ako.dbuff.dao.repo.KillLogRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.service.discord.DiscordMessageService;
import com.ako.dbuff.service.match.report.MatchReportOrchestrator.PartialReportResult;
import com.ako.dbuff.service.match.report.analyzer.ReportAnalyzer;
import com.ako.dbuff.service.match.report.formatter.ReportFormatter;
import com.ako.dbuff.service.match.report.stage.HeaderRenderer;
import com.ako.dbuff.service.match.report.stage.MatchReportStageService;
import com.ako.dbuff.service.match.report.stage.MatchThreadRecorder;
import com.ako.dbuff.service.match.report.stage.NewMatchSummary;
import java.util.List;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The switch decides between the staged header and the one channels have always had. */
class MatchReportOrchestratorStagesTest {

  private final DiscordMessageService discord = mock(DiscordMessageService.class);
  private final MatchReportStageService stages = mock(MatchReportStageService.class);
  private MatchReportOrchestrator orchestrator;
  private DbufInstanceConfigDomain config;

  @BeforeEach
  void setUp() {
    Message header = mock(Message.class);
    when(header.getIdLong()).thenReturn(5L);
    ThreadChannel thread = mock(ThreadChannel.class);
    when(thread.getIdLong()).thenReturn(6L);
    when(discord.sendChannelMessage(anyString(), anyString())).thenReturn(header);
    when(discord.createThread(any(), anyString())).thenReturn(thread);
    orchestrator =
        new MatchReportOrchestrator(
            discord,
            List.<ReportAnalyzer>of(),
            mock(ReportFormatter.class),
            mock(PlayerGameStatisticRepo.class),
            mock(AbilityRepo.class),
            mock(ItemRepository.class),
            mock(KillLogRepo.class),
            mock(MatchThreadRecorder.class),
            new HeaderRenderer(),
            stages);
    config = DbufInstanceConfigDomain.builder().id("i").discordChannelId("c").build();
  }

  @Test
  void switchedOff_theLegacyPlainHeaderAndThreadAreUsed() {
    when(stages.enabled()).thenReturn(false);

    PartialReportResult result =
        orchestrator.processAndReportPartial(
            MatchDomain.builder().id(9L).build(), config, NewMatchSummary.empty());

    assertThat(result).isEqualTo(new PartialReportResult(6L, 5L));
    verify(discord).sendChannelMessage("c", "Match 9 — waiting for parsed data...");
    verify(discord).createThread(any(), eq("Match 9"));
    verify(discord, never()).sendChannelComponents(anyString(), anyList());
    verify(stages, never()).start(any(), any(), any(), any());
  }

  @Test
  void switchedOn_theStageServiceTakesOver() {
    when(stages.enabled()).thenReturn(true);
    PartialReportResult staged = new PartialReportResult(1L, 2L);
    when(stages.start(any(), any(), any(), any())).thenReturn(staged);

    assertThat(
            orchestrator.processAndReportPartial(
                MatchDomain.builder().id(9L).build(), config, NewMatchSummary.empty()))
        .isSameAs(staged);
    verify(discord, never()).sendChannelMessage(anyString(), anyString());
  }

  @Test
  void aStagedHeader_isNotEditedAsTextByTheLegacyFullReport() {
    when(stages.enabled()).thenReturn(true);
    when(stages.ownsHeader(9L, "i")).thenReturn(true);
    Mockito.when(discord.getThreadById(6L)).thenReturn(null);

    orchestrator.processAndReportFull(MatchDomain.builder().id(9L).build(), config, 6L, 5L);

    verify(discord, never()).editMessage(anyString(), Mockito.anyLong(), anyString());
  }

  @Test
  void aLegacyPlainHeader_isStillEditedAsTextWhileStagesAreOn() {
    when(stages.enabled()).thenReturn(true);
    when(stages.ownsHeader(9L, "i")).thenReturn(false);

    orchestrator.processAndReportFull(MatchDomain.builder().id(9L).build(), config, 6L, 5L);

    verify(discord).editMessage(eq("c"), eq(5L), anyString());
  }

  @Test
  void switchedOff_theLegacyFullReportEditsTheHeader() {
    when(stages.enabled()).thenReturn(false);

    orchestrator.processAndReportFull(MatchDomain.builder().id(9L).build(), config, 6L, 5L);

    verify(discord).editMessage(eq("c"), eq(5L), anyString());
  }
}
