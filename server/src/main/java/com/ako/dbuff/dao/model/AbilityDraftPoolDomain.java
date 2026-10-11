package com.ako.dbuff.dao.model;

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

/** One ability of the Ability Draft pool, picked or skipped. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "poolIndex"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbilityDraftPoolDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ability_draft_pool_seq")
  @SequenceGenerator(
      name = "ability_draft_pool_seq",
      sequenceName = "ability_draft_pool_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Integer poolIndex;
  private Long abilityId;
  private String abilityName;

  /** Known for picked skills only; the replay does not say for a skipped one. */
  private Boolean ultimate;

  /** Null for a skipped skill. */
  private Long pickedBySlot;

  /** Null for a skipped skill. */
  private Integer pickOrder;
}
