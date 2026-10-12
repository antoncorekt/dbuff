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
 * A fight with a decided winner, seen from the team that lost it. The reasons themselves stay in
 * {@link FightReasonDomain}; this row only ties the fight to its swing and its cost.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "fightIndex"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FightExplanationDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fight_explanation_seq")
  @SequenceGenerator(
      name = "fight_explanation_seq",
      sequenceName = "fight_explanation_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Integer fightIndex;

  /** The swing whose evidence lists the fight; null for a fight outside every swing. */
  private Integer spikeIndex;

  /** RADIANT or DIRE. */
  private String losingTeam;

  /** Gold the losing team's deaths in the fight cost it; null when no death has a gold figure. */
  private Long goldLost;

  /** The losing team's deaths in the fight that no OpenDota death event could be matched to. */
  private Integer unmatchedDeaths;

  /** Every reason stored for the losing team in this fight, zero included. */
  private Integer reasonCount;

  private Integer analysisVersion;
}
