package com.ako.dbuff.service.match.report.analyzer;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LinkReport implements Report {

  private final String name;
  private final String url;
  private final String label;
}
