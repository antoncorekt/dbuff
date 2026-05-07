package com.ako.dbuff.service.match.report.analyzer;

import com.ako.dbuff.service.match.report.MatchReportContext;

public interface DataAvailability {

  boolean isApplicable(MatchReportContext context);
}
