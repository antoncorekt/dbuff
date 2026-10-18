package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.DbufInstanceConfigDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import java.util.List;
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
 * @param itemFilter the item categories or entry keys a person asked for in an on-demand request,
 *     null to follow the channel's setting; only the items section reads it
 */
public record ReplayReportInput(
    long matchId,
    MatchDomain match,
    Set<Long> focusSlots,
    Map<Long, String> namesBySlot,
    int maxFindings,
    String modelOverride,
    DbufInstanceConfigDomain instance,
    List<String> itemFilter) {

  public ReplayReportInput(
      long matchId,
      MatchDomain match,
      Set<Long> focusSlots,
      Map<Long, String> namesBySlot,
      int maxFindings,
      String modelOverride,
      DbufInstanceConfigDomain instance) {
    this(matchId, match, focusSlots, namesBySlot, maxFindings, modelOverride, instance, null);
  }

  public ReplayReportInput(
      long matchId,
      MatchDomain match,
      Set<Long> focusSlots,
      Map<Long, String> namesBySlot,
      int maxFindings,
      String modelOverride) {
    this(matchId, match, focusSlots, namesBySlot, maxFindings, modelOverride, null, null);
  }

  public ReplayReportInput(
      long matchId,
      MatchDomain match,
      Set<Long> focusSlots,
      Map<Long, String> namesBySlot,
      int maxFindings) {
    this(matchId, match, focusSlots, namesBySlot, maxFindings, null, null, null);
  }

  public ReplayReportInput withModelOverride(String model) {
    return new ReplayReportInput(
        matchId, match, focusSlots, namesBySlot, maxFindings, model, instance, itemFilter);
  }

  public ReplayReportInput withInstance(DbufInstanceConfigDomain channel) {
    return new ReplayReportInput(
        matchId, match, focusSlots, namesBySlot, maxFindings, modelOverride, channel, itemFilter);
  }

  public ReplayReportInput withItemFilter(List<String> names) {
    return new ReplayReportInput(
        matchId, match, focusSlots, namesBySlot, maxFindings, modelOverride, instance, names);
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
