package com.ako.dbuff.dao.model;

import jakarta.persistence.Column;
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

/** How the casts of one catalogued skill (see {@code replay/skills.yaml}) went for one hero. */
@Entity
@Table(
    name = "skill_effect",
    indexes = @Index(name = "idx_skill_effect_match", columnList = "matchId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillEffectDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "skill_effect_seq")
  @SequenceGenerator(
      name = "skill_effect_seq",
      sequenceName = "skill_effect_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Long playerSlot;

  /** The catalog entry. */
  private String entryKey;

  /** Casts of the hero; a missed chance is not a cast. */
  private Integer castCount;

  /** A JSON object with the count of each outcome. */
  @Column(columnDefinition = "text")
  private String figures;

  /** A JSON array with one object per counted cast or missed chance. */
  @Column(columnDefinition = "text")
  private String details;

  private Integer analysisVersion;
}
