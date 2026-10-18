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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Where one player was and how the hero was doing, sampled every {@code stepSeconds} from {@code
 * startSeconds} (negative: before 0:00). All arrays have {@code sampleCount} entries.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "playerSlot"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplayPlayerTrackDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "replay_player_track_seq")
  @SequenceGenerator(
      name = "replay_player_track_seq",
      sequenceName = "replay_player_track_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Long playerSlot;
  private Integer startSeconds;
  private Integer stepSeconds;
  private Integer sampleCount;

  /** World coordinates; the map spans about plus and minus 8200. */
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(columnDefinition = "integer array")
  private int[] x;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(columnDefinition = "integer array")
  private int[] y;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(columnDefinition = "integer array")
  private int[] hpPercent;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(columnDefinition = "integer array")
  private int[] manaPercent;

  /** Mana points; null for tracks stored before this was recorded. */
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(columnDefinition = "integer array")
  private int[] mana;

  /** Attack range in world units; null for tracks stored before this was recorded. */
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(columnDefinition = "integer array")
  private int[] attackRange;

  /** Zero before the hero exists. */
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(columnDefinition = "integer array")
  private int[] level;

  /** Bit 0 is set while the hero is alive. */
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(columnDefinition = "integer array")
  private int[] flags;

  /** Unspent gold (reliable plus unreliable); null for tracks stored before this was recorded. */
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(columnDefinition = "integer array")
  private int[] gold;

  /** Maximum health; null for tracks stored before this was recorded. */
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(columnDefinition = "integer array")
  private int[] maxHp;
}
