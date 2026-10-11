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

/** One pick of an Ability Draft, as the replay recorded it. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"matchId", "pickOrder"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbilityDraftPickDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ability_draft_pick_seq")
  @SequenceGenerator(
      name = "ability_draft_pick_seq",
      sequenceName = "ability_draft_pick_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;

  /** 1 to 40, in the order the picks happened. */
  private Integer pickOrder;

  private Long playerSlot;

  /** 0 = Radiant, 1 = Dire, as in {@link DraftEventDomain}. */
  private Integer team;

  /** 1-based position among the picks of the picker's team. */
  private Integer teamPickIndex;

  private Integer pickTick;

  /** Seconds since the first pick of the draft. */
  private Double secondsIntoDraft;

  /** Null for the first pick. */
  private Double secondsSincePrevPick;

  private Long abilityId;

  /** Null when the ability id is not in the constants. */
  private String abilityName;

  /** Position on the hero after the draft: 0-2 regular skills, 3 the ultimate. */
  private Integer finalAbilitySlot;

  private Boolean ultimate;

  /** Skills nobody had taken yet when this pick was made. */
  private Integer poolRemainingBeforePick;
}
