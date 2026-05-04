package com.ako.dbuff.resources.model;

import com.ako.dbuff.service.match.report.analyzer.Report;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MatchReportResponse {

  private Long matchId;
  private List<Report> reports;
}
