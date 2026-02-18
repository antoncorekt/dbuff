package com.ako.dbuff.dao.model;

import com.ako.dbuff.dao.model.id.AbilityId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Ability used by a player in a match.
 *
 * <p>Primary key uses playerSlot instead of playerId because: - Anonymous players have null
 * accountId (stored as -1 in playerId) - Multiple anonymous players in the same match would have
 * the same playerId - playerSlot (0-9) is always unique within a match
 */
@Entity
@IdClass(AbilityId.class)
@Builder
@ToString
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AbilityDomain {

  @Id private Long matchId;

  /** Player slot in the match (0-9). Part of the composite primary key. */
  @Id private Long playerSlot;

  @Id private Long abilityId;

  /** Player account ID. Can be -1 for anonymous players. Not part of the primary key. */
  private Long playerId;

  private String name;
  private String prettyName;

  /** Damage dealt by this ability (from damage_inflictor). */
  private Long damageDealt;

  /** Damage received from this ability (from damage_inflictor_received). */
  private Long damageReceived;

  /** Number of times this ability was used (from ability_uses). */
  private Long useCount;
}
