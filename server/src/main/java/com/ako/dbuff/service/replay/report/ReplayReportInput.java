package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
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
 * @param instance the channel the report is for, null when a caller has none
 */
public record ReplayReportInput(
    long matchId,
    MatchDomain match,
    Set<Long> focusSlots,
    Map<Long, String> namesBySlot,
    int maxFindings,
    String modelOverride,
    DbufInstanceConfigDomain instance) {

  public ReplayReportInput(
      long matchId,
      MatchDomain match,
      Set<Long> focusSlots,
      Map<Long, String> namesBySlot,
      int maxFindings,
      String modelOverride) {
    this(matchId, match, focusSlots, namesBySlot, maxFindings, modelOverride, null);
  }

  public ReplayReportInput(
      long matchId,
      MatchDomain match,
      Set<Long> focusSlots,
      Map<Long, String> namesBySlot,
      int maxFindings) {
    this(matchId, match, focusSlots, namesBySlot, maxFindings, null, null);
  }

  public ReplayReportInput withModelOverride(String model) {
    return new ReplayReportInput(
        matchId, match, focusSlots, namesBySlot, maxFindings, model, instance);
  }

  public ReplayReportInput withInstance(DbufInstanceConfigDomain channel) {
    return new ReplayReportInput(
        matchId, match, focusSlots, namesBySlot, maxFindings, modelOverride, channel);
  }

  /** The game mode of the match; null when the match or its mode is not known. */
  public Long gameModeId() {
    return match == null ? null : match.getGameModeId();
  }

  /** The language the channel chose for AI text; null when it follows the server's. */
  public String channelLanguage() {
    return instance == null ? null : instance.effectiveSettings().getLanguage();
  }
}
