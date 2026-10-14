package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.MatchDomain;
import java.util.Map;
import java.util.Set;

/**
 * What a section needs to write about one match for one channel.
 *
 * @param focusSlots the slots of the channel's tracked players in this match
 * @param namesBySlot the display name of each tracked player by slot
 * @param maxFindings the most findings the section may list
 * @param modelOverride the AI model a person asked for in an on-demand request, null for the
 *     default; only the AI sections read it
 */
public record ReplayReportInput(
    long matchId,
    MatchDomain match,
    Set<Long> focusSlots,
    Map<Long, String> namesBySlot,
    int maxFindings,
    String modelOverride) {

  public ReplayReportInput(
      long matchId,
      MatchDomain match,
      Set<Long> focusSlots,
      Map<Long, String> namesBySlot,
      int maxFindings) {
    this(matchId, match, focusSlots, namesBySlot, maxFindings, null);
  }

  public ReplayReportInput withModelOverride(String model) {
    return new ReplayReportInput(matchId, match, focusSlots, namesBySlot, maxFindings, model);
  }
}
