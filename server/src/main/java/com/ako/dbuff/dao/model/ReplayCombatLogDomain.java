package com.ako.dbuff.dao.model;

import jakarta.persistence.Column;
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
 * The hero-relevant combat log of one match as a compressed blob, so that fights, deaths and swings
 * can be worked out, and worked out again with other rules, after Valve has deleted the replay.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"matchId"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplayCombatLogDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "replay_combat_log_seq")
  @SequenceGenerator(
      name = "replay_combat_log_seq",
      sequenceName = "replay_combat_log_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;

  /** Layout of {@link #data}; see CombatLogCodec. */
  private Integer codecVersion;

  private Integer eventCount;

  @Column(columnDefinition = "bytea")
  private byte[] data;
}
