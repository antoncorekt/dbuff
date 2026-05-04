package com.ako.dbuff.service.match.report.analyzer;

import com.ako.dbuff.service.match.report.MatchReportContext;
import java.util.List;

public interface ReportAnalyzer {

  List<Report> analyze(MatchReportContext context);

  String getAnalyzerName();

  int getOrder();
}
