package com.ako.dbuff.service.match.report.handlers;

import com.ako.dbuff.service.match.report.MatchReportContext;
import com.ako.dbuff.service.match.report.analyzer.LinkReport;
import com.ako.dbuff.service.match.report.analyzer.Report;
import com.ako.dbuff.service.match.report.analyzer.ReportAnalyzer;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DotabuffLinkHandler implements ReportAnalyzer {

  @Override
  public List<Report> analyze(MatchReportContext context) {
    Long matchId = context.getMatch().getId();
    String url = String.format("https://dotabuff.com/matches/%d/builds", matchId);
    return List.of(new LinkReport("DotabuffLink", url, "Link to dotabuff"));
  }

  @Override
  public String getAnalyzerName() {
    return "DotabuffLink";
  }

  @Override
  public int getOrder() {
    return -1;
  }
}
