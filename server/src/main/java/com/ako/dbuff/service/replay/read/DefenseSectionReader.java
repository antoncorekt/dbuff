package com.ako.dbuff.service.replay.read;

import com.ako.dbuff.service.replay.analysis.defense.DefenseStatReader;
import com.ako.dbuff.service.replay.analysis.defense.DefenseStatReader.MatchDefense;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(85)
@RequiredArgsConstructor
class DefenseSectionReader implements AnalysisSectionReader {
  private final DefenseStatReader reader;

  @Override
  public String name() {
    return "defense";
  }

  @Override
  public MatchDefense read(long matchId, RunStatus run) {
    return reader.match(matchId);
  }
}
