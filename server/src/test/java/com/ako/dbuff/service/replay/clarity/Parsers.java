package com.ako.dbuff.service.replay.clarity;

import com.ako.dbuff.service.replay.ReplayProcessingProperties;
import java.util.List;

/** The parser wired as the application wires it. */
final class Parsers {
  private Parsers() {}

  static ClarityReplayParser standard() {
    return new ClarityReplayParser(
        List.of(new AbilityDraftReaderFactory()), new ReplayProcessingProperties());
  }
}
