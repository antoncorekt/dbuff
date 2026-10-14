package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.service.match.report.MatchReportOrchestrator;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * The old report made from OpenDota data (lanes, items, abilities, damage), available on demand as
 * {@code /match report opendota}. It posts automatically only when a section list names it.
 */
@Component
public class OpenDotaSection implements ReplayReportSection {
  public static final String NAME = "opendota";

  private final MatchReportOrchestrator orchestrator;

  public OpenDotaSection(@Lazy MatchReportOrchestrator orchestrator) {
    this.orchestrator = orchestrator;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public int order() {
    return 200;
  }

  @Override
  public ReportDetail minimumDetail() {
    return ReportDetail.STANDARD;
  }

  @Override
  public List<String> lines(ReplayReportInput input) {
    if (input.instance() == null) {
      return List.of();
    }
    return orchestrator.legacyMessages(input.match(), input.instance());
  }
}
