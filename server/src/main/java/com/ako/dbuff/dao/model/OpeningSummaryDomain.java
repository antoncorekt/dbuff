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
 * How one team did in the opening window around the first rune spawn: the runes it took, the kills,
 * and the gold and experience it received less what the other team received.
 */
@Entity
@Table(
    name = "opening_summary",
    uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "team"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpeningSummaryDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "opening_summary_seq")
  @SequenceGenerator(
      name = "opening_summary_seq",
      sequenceName = "opening_summary_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;

  /** RADIANT or DIRE. */
  private String team;

  private Integer windowStart;
  private Integer windowEnd;

  /** Runes the team took inside the window. */
  private Integer runesTaken;

  /** Bounty gold the team's heroes received inside the window (a rune pays every hero). */
  private Integer bountyGold;

  /** Bounty gold the team's heroes received over the game. */
  private Integer bountyGoldGame;

  /** The first of the team's pickups in the window; null when it took none. */
  private Long firstTakerSlot;

  private Integer firstTakerSeconds;

  /** Enemy heroes that died in the window. */
  private Integer kills;

  /** Gold and experience the team's heroes received in the window, deaths counted as losses. */
  private Long gold;

  private Long xp;

  /** Gold plus experience of the team less that of the other team. */
  private Long balance;

  /** WON, LOST or DRAW. */
  private String result;

  private Integer analysisVersion;
}
