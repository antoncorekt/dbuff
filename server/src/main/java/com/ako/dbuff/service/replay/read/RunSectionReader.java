package com.ako.dbuff.service.replay.read;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
class RunSectionReader implements AnalysisSectionReader {
  @Override
  public String name() {
    return "run";
  }

  @Override
  public Object read(long matchId, RunStatus run) {
    return run;
  }
}
