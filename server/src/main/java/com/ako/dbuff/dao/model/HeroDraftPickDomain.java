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

/** One hero of an Ability Draft hero pool, picked or left. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "poolIndex"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeroDraftPickDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hero_draft_pick_seq")
  @SequenceGenerator(
      name = "hero_draft_pick_seq",
      sequenceName = "hero_draft_pick_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Integer poolIndex;

  /** OpenDota hero id. */
  private Long heroId;

  /** Null for a hero nobody took. */
  private Long playerSlot;

  /** 0 = Radiant, 1 = Dire; null for a hero nobody took. */
  private Integer team;

  /** 1 to 50 over the skill and hero turns of the draft; null for a hero nobody took. */
  private Integer turnNumber;

  /** Null for a hero nobody took. */
  private Integer pickTick;

  /** Game clock at the pick, negative before the horn; null for a hero nobody took. */
  private Double clockSeconds;
}
