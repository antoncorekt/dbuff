package com.ako.dbuff.dao.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * How far a fight with a decided winner moved the gold and experience advantage graphs, seen from
 * the winner: the changes are positive when the winner gained. Only {@code important} is a verdict;
 * the other fields are the numbers and flags behind it.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "fightIndex"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FightImportanceDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fight_importance_seq")
  @SequenceGenerator(
      name = "fight_importance_seq",
      sequenceName = "fight_importance_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Integer fightIndex;

  /** RADIANT or DIRE. */
  private String winningTeam;

  /** The winner's gold advantage at the end of the window less at the start. */
  private Long goldChange;

  private Long xpChange;

  /** The winner's team net worth at the end of the window. */
  private Long netWorth;

  /** {@code goldChange} as a percentage of {@code netWorth}. */
  private Double sharePercent;

  /** The sign of the gold advantage changed inside the window. */
  private Boolean leadFlip;

  /** The flip left the winner in the lead. */
  private Boolean flipToWinner;

  /** The gold advantage moved in the other direction after the fight than before it. */
  private Boolean slopeBreak;

  private boolean important;
  private Integer analysisVersion;
}
