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

  /**
   * Whether the net worth timeline was read; false (or null on rows from version 1) means the lead
   * fields below are unknown, which is not the same as the loser never having led.
   */
  private Boolean leadKnown;

  /**
   * When the losing team's net worth lead was largest; null when it never led meaningfully (the
   * peak must reach the minimum gold and share) or the lead is not known.
   */
  private Integer peakLeadSeconds;

  /** The losing team's net worth lead at that time, in gold; null when it never led. */
  private Long peakLeadGold;

  /** That lead as a percentage of both teams' net worth together. */
  private Double peakLeadPercent;

  /**
   * The loser did not lead at the last whole minute sample of the net worth timeline, which can be
   * before the game ended; null when it never led or the lead is not known.
   */
  private Boolean leadLost;

  /** The loser's lead in gold at that last sample, negative when it trailed; null when unknown. */
  private Long lastLeadGold;

  /** The longest stretch the loser led by at least the throw share, in seconds. */
  private Integer heldSeconds;

  /**
   * The loser held at least the throw share and gold, from the throw minute on, for at least the
   * throw minimum, and was not leading at the last sample.
   */
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
