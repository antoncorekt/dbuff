package com.ako.dbuff.service.match.report.analyzer;

import com.ako.dbuff.service.match.report.MatchReportContext;

public interface FullDataAvailable extends DataAvailability {

  @Override
  default boolean isApplicable(MatchReportContext context) {
    return context.getItems() != null
        && !context.getItems().isEmpty()
        && context.getAbilities() != null
        && !context.getAbilities().isEmpty();
  }
}
