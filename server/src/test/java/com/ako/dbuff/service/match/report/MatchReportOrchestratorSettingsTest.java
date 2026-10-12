package com.ako.dbuff.service.match.report;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.InstanceSettings;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.dao.repo.AbilityRepo;
import com.ako.dbuff.dao.repo.ItemRepository;
import com.ako.dbuff.dao.repo.KillLogRepo;
import com.ako.dbuff.dao.repo.MatchReportThreadRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.service.discord.DiscordMessageService;
import com.ako.dbuff.service.match.report.analyzer.PartialDataAvailable;
import com.ako.dbuff.service.match.report.analyzer.Report;
import com.ako.dbuff.service.match.report.analyzer.ReportAnalyzer;
import com.ako.dbuff.service.match.report.formatter.ReportFormatter;
import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/** Proves the channel's detail level and AI switch decide which analyzers run. */
class MatchReportOrchestratorSettingsTest {

  /** Records that it ran; the declared minimum is the only thing that differs between them. */
  private static class RecordingAnalyzer implements ReportAnalyzer, PartialDataAvailable {
    private final String name;
    private final ReportDetail minimum;
    private final boolean ignoresDetail;
    final List<String> ran;

    RecordingAnalyzer(String name, ReportDetail minimum, boolean ignoresDetail, List<String> ran) {
      this.name = name;
      this.minimum = minimum;
      this.ignoresDetail = ignoresDetail;
      this.ran = ran;
    }

    @Override
    public List<Report> analyze(MatchReportContext context) {
      ran.add(name);
      return List.of();
    }

    @Override
    public String getAnalyzerName() {
      return name;
    }

    @Override
    public int getOrder() {
      return 0;
    }

    @Override
    public ReportDetail getMinimumDetail() {
      return minimum;
    }

    @Override
    public boolean isEnabledFor(InstanceSettings settings) {
      return ignoresDetail ? settings.isAiSummaryOn() : ReportAnalyzer.super.isEnabledFor(settings);
    }
  }

  private final List<String> ran = new ArrayList<>();
  private MatchReportOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    DiscordMessageService discord = Mockito.mock(DiscordMessageService.class);
    Mockito.when(discord.sendChannelMessage(Mockito.anyString(), Mockito.anyString()))
        .thenReturn(Mockito.mock(Message.class));
    Mockito.when(discord.createThread(Mockito.any(), Mockito.anyString()))
        .thenReturn(Mockito.mock(ThreadChannel.class));

    List<ReportAnalyzer> analyzers =
        List.of(
            new RecordingAnalyzer("link", ReportDetail.MINIMAL, false, ran),
            new RecordingAnalyzer("items", ReportDetail.STANDARD, false, ran),
            new RecordingAnalyzer("lane", ReportDetail.FULL, false, ran),
            new RecordingAnalyzer("ai", ReportDetail.MINIMAL, true, ran));

    orchestrator =
        new MatchReportOrchestrator(
            discord,
            analyzers,
            Mockito.mock(ReportFormatter.class),
            Mockito.mock(PlayerGameStatisticRepo.class),
            Mockito.mock(AbilityRepo.class),
            Mockito.mock(ItemRepository.class),
            Mockito.mock(KillLogRepo.class),
            Mockito.mock(MatchReportThreadRepo.class));
  }

  private void report(InstanceSettings settings) {
    DbufInstanceConfigDomain config =
        DbufInstanceConfigDomain.builder()
            .id("instance-1")
            .discordChannelId("channel-1")
            .settings(settings)
            .build();
    orchestrator.processAndReportPartial(MatchDomain.builder().id(1L).build(), config);
  }

  @Test
  void aChannelWithNoSettings_getsEveryAnalyzer() {
    report(null);

    assertThat(ran).containsExactlyInAnyOrder("link", "items", "lane", "ai");
  }

  @Test
  void minimalDetail_keepsOnlyTheLinkAndTheAiSummary() {
    report(InstanceSettings.builder().reportDetail(ReportDetail.MINIMAL).build());

    assertThat(ran).containsExactlyInAnyOrder("link", "ai");
  }

  @Test
  void standardDetail_addsTheStandardAnalyzers() {
    report(InstanceSettings.builder().reportDetail(ReportDetail.STANDARD).build());

    assertThat(ran).containsExactlyInAnyOrder("link", "items", "ai");
  }

  @Test
  void aiOff_dropsOnlyTheAiSummary_whateverTheDetail() {
    report(InstanceSettings.builder().aiSummaryEnabled(false).build());

    assertThat(ran).containsExactlyInAnyOrder("link", "items", "lane");
  }

  @Test
  void aiOn_survivesTheMostMinimalDetail() {
    report(
        InstanceSettings.builder()
            .reportDetail(ReportDetail.MINIMAL)
            .aiSummaryEnabled(true)
            .build());

    assertThat(ran).contains("ai");
  }
}
