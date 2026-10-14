package com.ako.dbuff.service.replay.read;

import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
@RequiredArgsConstructor
class RunSectionReader implements AnalysisSectionReader {
  private final RunStatusReader statusReader;

  @Override
  public String name() {
    return "run";
  }

  @Override
  public Object read(long matchId) {
    return statusReader.read(matchId).orElse(null);
  }
}
