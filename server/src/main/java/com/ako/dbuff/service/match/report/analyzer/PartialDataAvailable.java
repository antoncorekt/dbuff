package com.ako.dbuff.service.match.report.analyzer;

import com.ako.dbuff.service.match.report.MatchReportContext;

public interface PartialDataAvailable extends DataAvailability {

  @Override
  default boolean isApplicable(MatchReportContext context) {
    return true;
  }
}
