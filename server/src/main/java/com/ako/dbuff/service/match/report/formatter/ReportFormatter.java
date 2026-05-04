package com.ako.dbuff.service.match.report.formatter;

import com.ako.dbuff.service.match.report.analyzer.Report;
import java.util.List;
import java.util.Map;

public interface ReportFormatter {

  List<String> formatForDiscord(List<Report> reports, Map<Long, String> playerNames);
}
