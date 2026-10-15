package com.ako.dbuff.dao.model;

import com.ako.dbuff.service.replay.analysis.skillstat.SkillStatKind;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * How often, and how well, a hero, a skill, a hero with a skill or two skills held together did in
 * the Ability Draft games of the months before a match, for one player of that match.
 */
@Entity
@Table(
    name = "skill_pick_stat",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_skill_pick_stat_subject",
            columnNames = {"matchId", "playerSlot", "kind", "firstId", "secondId"}),
    indexes = {
      @Index(name = "idx_skill_pick_stat_match", columnList = "matchId"),
      @Index(name = "idx_skill_pick_stat_subject", columnList = "kind, firstId, matchId")
    })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillPickStatDomain {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "skill_pick_stat_seq")
  @SequenceGenerator(
      name = "skill_pick_stat_seq",
      sequenceName = "skill_pick_stat_seq",
      allocationSize = 50)
  private Long id;

  private Long matchId;
  private Long playerSlot;

  @Enumerated(EnumType.STRING)
  private SkillStatKind kind;

  /** The hero (HERO, HERO_SKILL) or the skill (SKILL) or the lower skill id of a pair. */
  private Long firstId;

  /** The skill of HERO_SKILL or the higher skill id of a pair; null otherwise. */
  private Long secondId;

  private Long games;
  private Long wins;

  /**
   * For a pair: the games and wins of each skill in the same window without the other one, the
   * baseline of the lift. Null for the other kinds.
   */
  private Long firstGamesWithout;

  private Long firstWinsWithout;
  private Long secondGamesWithout;
  private Long secondWinsWithout;

  private Integer windowMonths;
  private Integer analysisVersion;
}
