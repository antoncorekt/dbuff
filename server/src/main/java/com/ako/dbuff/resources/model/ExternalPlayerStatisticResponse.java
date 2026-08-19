package com.ako.dbuff.resources.model;

import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response model describing the history of games where a focus group of players played WITH (as
 * teammates) or AGAINST an external player identified by name.
 *
 * <p>All win/lose counts are computed from the focus group's perspective (i.e. whether a focus
 * player won or lost the match), while the per-match hero and skills describe the external player.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalPlayerStatisticResponse {

  /** Name of the external player that was searched for. */
  private String playerName;

  /** Account ID of the external player. Null if the player was not found. */
  private Long playerId;

  /** Win/lose counts for matches where the focus group played AGAINST the external player. */
  private WinLoseStat againstStat;

  /** Win/lose counts for matches where the focus group played WITH the external player. */
  private WinLoseStat teammateStat;

  /** Per-match history entries, one per qualifying match. */
  private List<HistoryEntry> history;

  /** Win/lose counters. */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class WinLoseStat {
    /** Number of matches won. */
    private long win;

    /** Number of matches lost. */
    private long lose;
  }

  /** A single match in which a focus player met the external player. */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class HistoryEntry {
    /** Match ID. */
    private Long matchId;

    /** Date the match was played. May be null if unknown. */
    private LocalDate matchDate;

    /** Dotabuff URL for the match builds. */
    private String dotabuffLink;

    /** Whether the external player was on the opposing team of the focus player. */
    private boolean against;

    /** Whether the external player was on the same team as the focus player. */
    private boolean teammate;

    /** Whether the focus player won this match. */
    private boolean playerWon;

    /** Statistics describing the external player in this match. */
    private MatchStats matchStats;
  }

  /** Statistics describing the external player within a single match. */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class MatchStats {
    /** Hero (pretty name) played by the external player. */
    private String playerHero;

    /** Skills/abilities (pretty names) chosen by the external player. */
    private List<String> playerSkills;
  }
}
