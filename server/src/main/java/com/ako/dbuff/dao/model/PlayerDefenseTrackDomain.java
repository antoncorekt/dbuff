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
 * One hero's health, estimated armor and magic resistance and effective health at every game minute
 * from {@code startMinute}. A minute with no value holds {@link #MISSING}; an array is null when
 * the stored track lacks what it is computed from.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "playerSlot"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerDefenseTrackDomain {
  public static final int MISSING = Integer.MIN_VALUE;

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "player_defense_track_seq")
  @SequenceGenerator(
      name = "player_defense_track_seq",
      sequenceName = "player_defense_track_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Long playerSlot;
  private Integer startMinute;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(columnDefinition = "integer array")
  private int[] maxHp;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(columnDefinition = "integer array")
  private int[] hp;

  /** Estimated armor times 10. */
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(columnDefinition = "integer array")
  private int[] armorX10;

  /** Estimated magic resistance in percent times 10. */
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(columnDefinition = "integer array")
  private int[] magicResistX10;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(columnDefinition = "integer array")
  private int[] ehpPhys;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(columnDefinition = "integer array")
  private int[] ehpMag;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(columnDefinition = "integer array")
  private int[] ehpMix;

  private Integer analysisVersion;
}
