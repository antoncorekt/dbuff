package com.ako.dbuff.service.match.report.handlers;

import com.ako.dbuff.service.match.report.MatchReportContext;
import com.ako.dbuff.service.match.report.analyzer.FullDataAvailable;
import com.ako.dbuff.service.match.report.analyzer.Report;
import com.ako.dbuff.service.match.report.analyzer.ReportAnalyzer;
import com.ako.dbuff.service.match.report.analyzer.TextReport;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AiSummaryReportHandler implements ReportAnalyzer, FullDataAvailable {

  @Override
  public List<Report> analyze(MatchReportContext context) {
    log.info("AI analysis not yet implemented for match {}", context.getMatch().getId());
    return List.of(
        new TextReport(
            "AiSummary",
            "AI analysis for match " + context.getMatch().getId() + " — coming soon."));
  }

  @Override
  public String getAnalyzerName() {
    return "AiSummary";
  }

  @Override
  public int getOrder() {
    return 5;
  }
}
