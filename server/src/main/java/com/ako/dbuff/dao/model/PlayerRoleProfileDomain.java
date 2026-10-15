package com.ako.dbuff.dao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What a player was meant to do (stage INTENT, from the draft) or did (stage FINAL, from the
 * match), as ten functions and the role that fits them. A FINAL row repeats the role of {@link
 * PlayerRoleDomain} so that a report reads one table. A function is null when nothing known tells
 * it.
 */
@Entity
@Table(
    name = "player_role_profile",
    indexes = @Index(name = "idx_player_role_profile_match", columnList = "matchId"),
    uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "playerSlot", "stage"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerRoleProfileDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "player_role_profile_seq")
  @SequenceGenerator(
      name = "player_role_profile_seq",
      sequenceName = "player_role_profile_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Long playerSlot;

  /** INTENT or FINAL. */
  private String stage;

  /** 1 carry, 2 mid, 3 offlane, 4 roaming or jungle support, 5 hard support. */
  private Integer role;

  /**
   * The best role by {@code roleScores} alone, which may differ from {@code role} (the resolver's),
   * and is null when not worked out.
   */
  private Integer fitRole;

  /** 0 to 1, as in {@link PlayerRoleDomain}. */
  private Double confidence;

  /** SAFE, MID or OFF; null when unknown. */
  private String lane;

  private Double farmPriority;
  private Double jungle;
  private Double roaming;
  private Double visionDuty;
  private Double initiation;
  private Double disable;
  private Double saveHeal;
  private Double damage;
  private Double durability;

  /** Only the intent stage knows it; null in the final stage. */
  @Column(name = "escape_function")
  private Double escape;

  /** The score of the profile in roles 1 to 5, comma separated. */
  private String roleScores;

  @Column(length = 400)
  private String evidence;

  private Integer analysisVersion;
}
