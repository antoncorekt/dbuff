package com.ako.dbuff.dao.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** How a match ended: who won, the lead the loser held, the turning point and the buybacks. */
@Entity
@Table(
    name = "end_game_summary",
    indexes = @Index(name = "idx_end_game_summary_match", columnList = "matchId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EndGameSummaryDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "end_game_summary_seq")
  @SequenceGenerator(
      name = "end_game_summary_seq",
      sequenceName = "end_game_summary_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;

  /** RADIANT or DIRE. */
  private String winnerTeam;

  private Integer durationSeconds;

  /** When the losing team's net worth lead was largest; null when it never led. */
  private Integer peakLeadSeconds;

  /** The losing team's net worth lead at that time, in gold; null when it never led. */
  private Long peakLeadGold;

  /** That lead as a percentage of both teams' net worth together. */
  private Double peakLeadPercent;

  /** The loser no longer led at the last minute; null when it never led. */
  private Boolean leadLost;

  /** The longest stretch the loser led by at least the throw share, in seconds. */
  private Integer heldSeconds;

  /** The loser held at least the throw share for at least the throw minimum, and then lost. */
  private Boolean thrown;

  private Integer turningFightIndex;
  private Double turningSeconds;

  /** CONVERTED (the winner's fight was followed by an objective) or SWING (the largest swing). */
  private String turningKind;

  private String objectiveKind;
  private String objectiveKey;
  private Integer objectiveSecondsAfter;

  /** The winner's gold advantage change in the turning fight's window. */
  private Long turningGoldChange;

  /** Buybacks each team paid for in the last window of the game. */
  private Integer radiantBuybacks;

  private Integer direBuybacks;
  private Integer buybackWindowSeconds;

  /** The last important fight, or the last fight when none was important. */
  private Integer finalFightIndex;

  private Double finalFightSeconds;

  /** RADIANT, DIRE or DRAW. */
  private String finalFightWinner;

  private Integer analysisVersion;
}
