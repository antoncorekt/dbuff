package com.ako.dbuff.dao.model;

import jakarta.persistence.*;
import lombok.*;

/** Damage dealt, or ability casts, by one player on one enemy hero, split by inflictor. */
@Entity
@Table(
    uniqueConstraints =
        @UniqueConstraint(
            columnNames = {"matchId", "playerSlot", "statKind", "inflictor", "targetHeroName"}),
    indexes = @Index(name = "idx_hero_target_stat_match", columnList = "matchId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeroTargetStatDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hero_target_stat_seq")
  @SequenceGenerator(
      name = "hero_target_stat_seq",
      sequenceName = "hero_target_stat_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Long playerSlot;
  /** Account id, -1 for anonymous players. */
  private Long playerId;
  @Enumerated(EnumType.STRING)
  private TargetStatKind statKind;
  /** Ability or item key; "null" means plain attacks. */
  private String inflictor;
  /** Internal hero name of the target. */
  private String targetHeroName;
  /** Damage points, or number of casts for ABILITY_USE. */
  private Long amount;
}
