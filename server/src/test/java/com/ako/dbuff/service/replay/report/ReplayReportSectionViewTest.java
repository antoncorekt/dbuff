package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.service.replay.report.view.Accent;
import com.ako.dbuff.service.replay.report.view.SectionView;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayReportSectionViewTest {

  private static ReplayReportSection of(String... lines) {
    return new ReplayReportSection() {
      @Override
      public String name() {
        return "legacy";
      }

      @Override
      public int order() {
        return 1;
      }

      @Override
      public ReportDetail minimumDetail() {
        return ReportDetail.STANDARD;
      }

      @Override
      public List<String> lines(ReplayReportInput input) {
        return List.of(lines);
      }
    };
  }

  @Test
  void aLeadingBoldLineBecomesTheTitleAndLeavesTheBullets() {
    ReplayReportSection section = of("**Deaths**", "10:00 died", "12:00 died");

    SectionView view = section.view(null).orElseThrow();

    assertThat(view.title()).isEqualTo("Deaths");
    assertThat(view.bullets()).containsExactly("10:00 died", "12:00 died");
    assertThat(view.accent()).isEqualTo(Accent.INFO);
    assertThat(section.lines(null)).containsExactly("**Deaths**", "10:00 died", "12:00 died");
  }

  @Test
  void aTitleLineWithBlanksAroundItIsStillATitle() {
    assertThat(of("  **Deaths**  ", "x").view(null).orElseThrow().title()).isEqualTo("Deaths");
  }

  @Test
  void aLineWithBoldInsideIsNotATitleAndNoLinesIsNoView() {
    assertThat(of("**a** and **b**", "x").view(null).orElseThrow().title()).isEmpty();
    assertThat(of().view(null)).isEmpty();
  }
}
