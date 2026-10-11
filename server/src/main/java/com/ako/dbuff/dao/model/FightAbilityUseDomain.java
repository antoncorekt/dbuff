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

/**
 * What one hero did with one of his abilities or items in one fight, and when he did not use it,
 * why not. Only things he was meant to use are listed: learned abilities and held items that were
 * seen on cooldown, had a mana cost or were used at some point in the match.
 */
@Entity
@Table(
    indexes =
        @Index(name = "idx_fight_ability_use_match_fight", columnList = "matchId, fightIndex"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FightAbilityUseDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fight_ability_use_seq")
  @SequenceGenerator(
      name = "fight_ability_use_seq",
      sequenceName = "fight_ability_use_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Integer fightIndex;
  private Long playerSlot;

  /** The game's internal ability or item name. */
  private String abilityName;

  /** ABILITY or ITEM. */
  private String kind;

  /** The skill level at the start of the fight; 0 for an item. */
  private Integer level;

  private Boolean readyAtStart;

  /** Seconds until it was ready, rounded up; 0 when ready. */
  private Integer cooldownLeftAtStart;

  private Integer manaCost;

  /** Null when the track has no mana points (replays stored before they were recorded). */
  private Integer manaAtStart;

  private Integer casts;
  private Double firstCastSeconds;

  /** Damage to heroes done with it during the fight. */
  private Long damage;

  /** Filled when it was not cast in the fight: the last cast before it, if any. */
  private Double lastCastBeforeSeconds;

  /** True when that last cast hurt creeps and no hero. Null when there was no earlier cast. */
  private Boolean lastCastOnCreeps;

  /** One of the NonCastReason values; null when it was cast. */
  private String nonCastReason;

  private Integer analysisVersion;
}
