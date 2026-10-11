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
 * A fight found in the replay's combat log: hero-to-hero damage and deaths that follow each other
 * closely. Wider than OpenDota's {@code TeamfightDomain}, which needs three deaths.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "fightIndex"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FightDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fight_seq")
  @SequenceGenerator(name = "fight_seq", sequenceName = "fight_seq", allocationSize = 50)
  private Long id;

  private Long matchId;

  /** 0-based, in time order. */
  private Integer fightIndex;

  /** TEAMFIGHT, SKIRMISH or ENGAGEMENT. */
  private String type;

  private Double startSeconds;
  private Double endSeconds;

  /** Null when nobody died. */
  private Double firstDeathSeconds;

  private Integer centerX;
  private Integer centerY;

  /** Zone kind at the centre of the fight: BASE, LANE, RIVER or JUNGLE; null without a map. */
  private String zone;

  private String lane;
  private Integer radiantDeaths;
  private Integer direDeaths;
  private Integer radiantParticipants;
  private Integer direParticipants;
  private Long radiantHeroDamage;
  private Long direHeroDamage;

  /** RADIANT, DIRE or DRAW. */
  private String winner;

  private Integer analysisVersion;
}
