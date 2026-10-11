package com.ako.dbuff.service.match.report.analyzer;

import com.ako.dbuff.dao.model.InstanceSettings;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.service.match.report.MatchReportContext;
import java.util.List;

public interface ReportAnalyzer {

  List<Report> analyze(MatchReportContext context);

  String getAnalyzerName();

  int getOrder();

  /**
   * The lowest report detail level at which a channel sees this analyzer. Defaults to {@link
   * ReportDetail#FULL}, so a new analyzer stays out of the shorter reports until someone decides it
   * belongs in them.
   */
  default ReportDetail getMinimumDetail() {
    return ReportDetail.FULL;
  }

  /** Whether a channel with these settings gets this analyzer's report. */
  default boolean isEnabledFor(InstanceSettings settings) {
    return settings.detail().includes(getMinimumDetail());
  }
}
