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
 * An item gained or lost, an item or ability going on cooldown, or a skill level gained. Seen on
 * the replay's sampling grid, so {@code gameSeconds} is accurate to a few seconds; a cooldown's
 * {@code readyAtSeconds} is exact.
 */
@Entity
@Table(
    indexes =
        @Index(name = "idx_replay_state_change_match_player", columnList = "matchId, playerSlot"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplayStateChangeDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "replay_state_change_seq")
  @SequenceGenerator(
      name = "replay_state_change_seq",
      sequenceName = "replay_state_change_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Long playerSlot;
  private Double gameSeconds;

  /** One of the StateChangeKind values. */
  private String kind;

  private String name;

  /** Inventory slot for an item, ability slot for an ability; -1 when unknown. */
  private Integer slotIndex;

  /** For a cooldown, the second it ends. */
  private Double readyAtSeconds;

  /** For a cooldown, its full length in seconds; 0 when the replay does not say. */
  private Double cooldownLength;

  /** For an ability level change, the new level. */
  private Integer abilityLevel;
}
