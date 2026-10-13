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
 * One lane or camp creep a hero killed, from the stored combat log. Gold and experience are the
 * hero's own entries at the same moment, null where they could not be tied to this kill; the place
 * is the one the gold or experience entry gives, so it is null for a deny.
 */
@Entity
@Table(indexes = @Index(name = "idx_creep_kill_match", columnList = "matchId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreepKillDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "creep_kill_seq")
  @SequenceGenerator(name = "creep_kill_seq", sequenceName = "creep_kill_seq", allocationSize = 50)
  private Long id;

  private Long matchId;
  private Long playerSlot;
  private Double seconds;

  /** One of the CreepClass values. */
  private String creepClass;

  /** The unit name as the combat log has it. */
  private String creepName;

  /** OWN or ENEMY, seen from the killer; null for a camp creep. */
  private String side;

  /** True when the killer's own side's lane creep was killed. */
  private Boolean denied;

  private Integer gold;

  /** What a flagbearer paid the killer on top of its own gold; null for other creeps. */
  private Integer bonusGold;

  private Integer xp;

  /** How many heroes received experience at that moment, so the killer's share can be read. */
  private Integer xpRecipients;

  /** BASE, LANE, RIVER or JUNGLE; null without a position or a frozen map layout. */
  private String zone;

  private String lane;

  /** The camp's index in the map layout; null outside a camp. */
  private Integer camp;

  private Integer analysisVersion;
}
