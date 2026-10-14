package com.ako.dbuff.service.ai.reasons;

import com.ako.dbuff.service.replay.report.ReplayReportInput;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assembles the labelled facts about one match for the model: the result, heroes, per-hero numbers
 * and roles from the match, and what the replay report sections already say. Missing data means a
 * missing fact, never an error. Facts carry hero names and times as players read them, no internal
 * ids and no Discord ids.
 */
@Component
@RequiredArgsConstructor
public class AiMatchFactsBuilder {
  private final MatchBasicFacts basic;
  private final SectionFacts sectionFacts;

  public List<Fact> build(ReplayReportInput input) {
    ReplayReportInput anonymous =
        new ReplayReportInput(
            input.matchId(), input.match(), input.focusSlots(), Map.of(), input.maxFindings());
    List<Fact> facts = new ArrayList<>(basic.build(anonymous));
    facts.addAll(sectionFacts.build(anonymous));
    return List.copyOf(facts);
  }
}
