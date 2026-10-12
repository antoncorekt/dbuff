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
 * What surrounded the fall of a tower, barracks or ancient: which kind and lane, whose it was, who
 * took it, which of the attackers were close in the ten seconds before, and the fight it came in.
 * Time and killer also stay in OpenDota's {@code ObjectiveEventDomain}.
 */
@Entity
@Table(indexes = @Index(name = "idx_building_kill_context_match", columnList = "matchId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuildingKillContextDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "building_kill_context_seq")
  @SequenceGenerator(
      name = "building_kill_context_seq",
      sequenceName = "building_kill_context_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;

  /** The building's name as the combat log has it. */
  private String buildingName;

  private Double seconds;

  /** TOWER, BARRACKS_MELEE, BARRACKS_RANGED or FORT. */
  private String kind;

  /** 1 to 4 for a tower; null for the rest. */
  private Integer tier;

  /** TOP, MID or BOTTOM; null for a building that is on no lane. */
  private String lane;

  /** RADIANT or DIRE: whose building it was. */
  private String ownerTeam;

  /** The hero who took it, on OpenDota's slot; null when creeps did. */
  private Long killerSlot;

  /** True when the killer was on the owner's own team. */
  private Boolean denied;

  /** Heroes of the attacking team within 1500 units of it in the ten seconds before. */
  private Integer attackersNear;

  /** Their slots, comma separated. */
  private String attackerSlots;

  private Integer fightIndex;
  private Integer analysisVersion;
}
