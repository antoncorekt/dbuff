package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.FightVisionDomain;
import com.ako.dbuff.dao.model.TeamRotationDomain;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.analysis.vision.VisionVerdict;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One fight as {@link FightContext} holds it, with the tables of the analyzers that run after the
 * first reason pass.
 *
 * @param visions the fight's vision rows, one per team; empty for a fight that is not important
 * @param rotations every rotation of the match
 */
public record LateFightContext(
    FightContext fight, List<FightVisionDomain> visions, List<TeamRotationDomain> rotations) {

  /** The newest row (largest id) when a team has several, so the choice never depends on order. */
  public Optional<FightVisionDomain> vision(TeamSide team) {
    return visions.stream()
        .filter(v -> team.name().equals(v.getTeam()))
        .max(
            Comparator.comparing(
                FightVisionDomain::getId, Comparator.nullsFirst(Comparator.naturalOrder())));
  }

  /** The verdicts a vision row stored, which the analyzer keeps as comma separated codes. */
  public static Set<VisionVerdict> verdicts(FightVisionDomain vision) {
    if (vision.getVerdict() == null) {
      return Set.of();
    }
    return Arrays.stream(vision.getVerdict().split(","))
        .map(String::trim)
        .map(code -> Enums.find(VisionVerdict.class, code))
        .filter(v -> v != null)
        .collect(Collectors.toSet());
  }
}
