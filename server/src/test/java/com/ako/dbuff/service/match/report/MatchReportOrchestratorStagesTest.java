package com.ako.dbuff.service.match.report;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.repo.AbilityRepo;
import com.ako.dbuff.dao.repo.ItemRepository;
import com.ako.dbuff.dao.repo.KillLogRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.service.discord.DiscordMessageService;
import com.ako.dbuff.service.match.report.MatchReportOrchestrator.PartialReportResult;
import com.ako.dbuff.service.match.report.analyzer.Report;
import com.ako.dbuff.service.match.report.analyzer.ReportAnalyzer;
import com.ako.dbuff.service.match.report.formatter.ReportFormatter;
import com.ako.dbuff.service.match.report.handlers.AiSummaryReportHandler;
import com.ako.dbuff.service.match.report.stage.HeaderRenderer;
import com.ako.dbuff.service.match.report.stage.MatchReportStageService;
import com.ako.dbuff.service.match.report.stage.MatchReportStageService.HeaderKind;
import com.ako.dbuff.service.match.report.stage.MatchThreadRecorder;
import com.ako.dbuff.service.match.report.stage.NewMatchSummary;
import java.util.List;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
  private final ReportFormatter formatter = mock(ReportFormatter.class);
  private final AbilityRepo abilityRepo = mock(AbilityRepo.class);
  private final ItemRepository itemRepository = mock(ItemRepository.class);
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
            List.<ReportAnalyzer>of(new AiSummaryReportHandler()),
            formatter,
            mock(PlayerGameStatisticRepo.class),
            abilityRepo,
            itemRepository,
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
    when(stages.headerKind(9L, "i")).thenReturn(HeaderKind.STAGED);
    Mockito.when(discord.getThreadById(6L)).thenReturn(null);

    orchestrator.processAndReportFull(MatchDomain.builder().id(9L).build(), config, 6L, 5L);

    verify(discord, never()).editMessage(anyString(), Mockito.anyLong(), anyString());
  }

  @Test
  void aLegacyPlainHeader_isStillEditedAsTextWhileStagesAreOn() {
    when(stages.enabled()).thenReturn(true);
    when(stages.headerKind(9L, "i")).thenReturn(HeaderKind.PLAIN);

    orchestrator.processAndReportFull(MatchDomain.builder().id(9L).build(), config, 6L, 5L);

    verify(discord).editMessage(eq("c"), eq(5L), anyString());
  }

  @Test
  void switchedOff_anUnknownHeaderIsEditedAsBefore() {
    when(stages.enabled()).thenReturn(false);
    when(stages.headerKind(9L, "i")).thenReturn(HeaderKind.UNKNOWN);

    orchestrator.processAndReportFull(MatchDomain.builder().id(9L).build(), config, 6L, 5L);

    verify(discord).editMessage(eq("c"), eq(5L), anyString());
  }

  @Test
  void anUnknownHeader_isNotEditedByTheLegacyFullReport() {
    when(stages.enabled()).thenReturn(true);
    when(stages.headerKind(9L, "i")).thenReturn(HeaderKind.UNKNOWN);

    orchestrator.processAndReportFull(MatchDomain.builder().id(9L).build(), config, 6L, 5L);

    verify(discord, never()).editMessage(anyString(), Mockito.anyLong(), anyString());
  }

  @Test
  void switchedOff_theLegacyFullReportEditsTheHeader() {
    when(stages.enabled()).thenReturn(false);
    when(stages.headerKind(9L, "i")).thenReturn(HeaderKind.PLAIN);

    orchestrator.processAndReportFull(MatchDomain.builder().id(9L).build(), config, 6L, 5L);

    verify(discord).editMessage(eq("c"), eq(5L), anyString());
  }

  private void fullDataStored() {
    when(abilityRepo.findAllByMatchId(9L)).thenReturn(List.of(mock(AbilityDomain.class)));
    when(itemRepository.findAllByMatchId(9L)).thenReturn(List.of(mock(ItemDomain.class)));
    when(formatter.formatForDiscord(anyList(), any())).thenReturn(List.of("old report"));
    when(discord.getThreadById(6L)).thenReturn(mock(ThreadChannel.class));
    when(stages.headerKind(9L, "i")).thenReturn(HeaderKind.STAGED);
  }

  @Test
  void switchedOn_theFullStagePostsNoOldReportAndNoAiPlaceholder() {
    fullDataStored();
    when(stages.enabled()).thenReturn(true);

    orchestrator.processAndReportFull(MatchDomain.builder().id(9L).build(), config, 6L, 5L);

    verify(formatter, never()).formatForDiscord(anyList(), any());
    verify(discord, never()).sendThreadMessageBlocking(any(), anyString());
  }

  @Test
  void switchedOff_theFullStageStillPostsTheOldReportWithTheAiPlaceholder() {
    fullDataStored();
    when(stages.enabled()).thenReturn(false);

    orchestrator.processAndReportFull(MatchDomain.builder().id(9L).build(), config, 6L, 5L);

    ArgumentCaptor<List<Report>> reports = ArgumentCaptor.forClass(List.class);
    verify(formatter).formatForDiscord(reports.capture(), any());
    assertThat(reports.getValue()).hasSize(1);
    verify(discord).sendThreadMessageBlocking(any(), eq("old report"));
  }

  @Test
  void switchedOn_aPlainHeaderIsEditedAsTextButNoOldReportIsPosted() {
    fullDataStored();
    when(stages.enabled()).thenReturn(true);
    when(stages.headerKind(9L, "i")).thenReturn(HeaderKind.PLAIN);

    orchestrator.processAndReportFull(MatchDomain.builder().id(9L).build(), config, 6L, 5L);

    verify(discord).editMessage(eq("c"), eq(5L), anyString());
    verify(formatter, never()).formatForDiscord(anyList(), any());
    verify(discord, never()).sendThreadMessageBlocking(any(), anyString());
  }

  @Test
  void switchedOn_aStagedOrUnknownHeaderDoesNothingAtAll() {
    fullDataStored();
    when(stages.enabled()).thenReturn(true);
    when(stages.headerKind(9L, "i")).thenReturn(HeaderKind.UNKNOWN);

    orchestrator.processAndReportFull(MatchDomain.builder().id(9L).build(), config, 6L, 5L);

    verify(discord, never()).editMessage(anyString(), Mockito.anyLong(), anyString());
    verify(discord, never()).sendThreadMessageBlocking(any(), anyString());
  }

  @Test
  void analyzeMatch_staysAvailableOnDemandWhateverTheSwitch() {
    when(stages.enabled()).thenReturn(true);

    assertThat(orchestrator.analyzeMatch(MatchDomain.builder().id(9L).build(), config)).hasSize(1);
  }
}
