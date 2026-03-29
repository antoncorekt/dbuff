package com.ako.dbuff.service.match.report.handlers;

import com.ako.dbuff.service.match.report.MatchReportContext;
import com.ako.dbuff.service.match.report.MatchReportHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AiSummaryReportHandler implements MatchReportHandler {

  @Override
  public String handle(MatchReportContext context) {
    log.info("AI analysis not yet implemented for match {}", context.getMatch().getId());
    return "AI analysis for match " + context.getMatch().getId() + " — coming soon.";
  }

  @Override
  public int getOrder() {
    return 3;
  }

  @Override
  public String getHandlerName() {
    return "AiSummary";
  }
}
