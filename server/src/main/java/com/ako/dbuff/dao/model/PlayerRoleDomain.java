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

/** The role a player filled in a match; each team has each role once. */
@Entity
@Table(
    name = "player_role",
    indexes = @Index(name = "idx_player_role_match", columnList = "matchId"),
    uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "playerSlot"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerRoleDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "player_role_seq")
  @SequenceGenerator(
      name = "player_role_seq",
      sequenceName = "player_role_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Long playerSlot;

  /** 1 carry, 2 mid, 3 offlane, 4 roaming or jungle support, 5 hard support. */
  private Integer role;

  /** ASSIGNED when the game gave the role, DERIVED when it was worked out from the match. */
  private String source;

  /** 0 (a coin flip between roles) to 1 (no other assignment comes close). */
  private Double confidence;

  @Column(length = 400)
  private String evidence;

  private Integer analysisVersion;
}
