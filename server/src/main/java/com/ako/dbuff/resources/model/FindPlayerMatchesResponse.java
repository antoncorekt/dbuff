package com.ako.dbuff.resources.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response model for finding player matches with statistics. Contains match information and
 * statistics for the searched player and default players.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FindPlayerMatchesResponse {

  /** Match ID */
  private Long matchId;

  /** Match date */
  private LocalDate matchDate;

  /** Dotabuff URL for the match builds */
  private String dotabuffUrl;

  /** ID of the searched player */
  private Long playerId;

  /** Name of the searched player */
  private String playerName;

  /** Statistics for players in this match (searched player + default players) */
  private List<PlayerMatchStatistic> statistics;

  /** Player match statistic details */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PlayerMatchStatistic {
    /** Player ID */
    private Long playerId;

    /** Player name */
    private String playerName;

    /** Player slot in the match (0-4 Radiant, 128-132 Dire) */
    private Long playerSlot;

    /** Hero pretty name */
    private String heroPrettyName;

    /** KDA ratio */
    private BigDecimal kda;

    /** Win (1) or Loss (0) */
    private Long win;

    /** Gold per minute */
    private Long goldPerMin;
  }
}
