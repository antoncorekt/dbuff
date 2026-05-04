package com.ako.dbuff.service.match.report.analyzer;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TextReport implements Report {

  private final String name;
  private final String text;
}
