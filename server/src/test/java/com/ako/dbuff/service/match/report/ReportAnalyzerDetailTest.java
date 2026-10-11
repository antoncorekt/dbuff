package com.ako.dbuff.service.match.report;

import com.ako.dbuff.dao.model.InstanceSettings;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.service.constant.ConstantPrecacheStarter;
import com.ako.dbuff.service.match.report.analyzer.ReportAnalyzer;
import com.ako.dbuff.service.match.report.handlers.AiSummaryReportHandler;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real analyzers' declared detail levels. The default is FULL, so an analyzer added later falls
 * out of the shorter reports until someone places it; this pins where the current ones sit.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReportAnalyzerDetailTest {

  @MockBean private JDA jda;
  @MockBean private ConstantPrecacheStarter constantPrecacheStarter;

  @Autowired private List<ReportAnalyzer> analyzers;

  private Map<String, ReportAnalyzer> bySimpleName() {
    return analyzers.stream()
        .collect(Collectors.toMap(a -> AopUtils.getTargetClass(a).getSimpleName(), a -> a));
  }

  @Test
  void theLinkIsInEveryReport_andTheRestWaitForMoreDetail() {
    Map<String, ReportAnalyzer> byName = bySimpleName();

    assertThat(byName.get("DotabuffLinkHandler").getMinimumDetail())
        .isEqualTo(ReportDetail.MINIMAL);
    assertThat(byName.get("ItemReporterAnalyzer").getMinimumDetail())
        .isEqualTo(ReportDetail.STANDARD);
    assertThat(byName.get("AbilityReporterAnalyzer").getMinimumDetail())
        .isEqualTo(ReportDetail.STANDARD);
    for (String full :
        List.of(
            "DamageReporterAnalyzer",
            "ItemEfficiencyAnalyzer",
            "LaneAnalyzer",
            "OtherPlayerAnalyzer")) {
      assertThat(byName.get(full).getMinimumDetail()).as(full).isEqualTo(ReportDetail.FULL);
    }
  }

  @Test
  void theAiSummary_followsItsOwnSwitchNotTheDetailLevel() {
    ReportAnalyzer ai =
        analyzers.stream()
            .filter(AiSummaryReportHandler.class::isInstance)
            .findFirst()
            .orElseThrow();

    InstanceSettings minimalWithAi =
        InstanceSettings.builder().reportDetail(ReportDetail.MINIMAL).build();
    InstanceSettings fullWithoutAi =
        InstanceSettings.builder().reportDetail(ReportDetail.FULL).aiSummaryEnabled(false).build();

    assertThat(ai.isEnabledFor(minimalWithAi)).isTrue();
    assertThat(ai.isEnabledFor(fullWithoutAi)).isFalse();
  }
}
