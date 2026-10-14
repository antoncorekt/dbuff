package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.FightVisionDomain;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.analysis.vision.VisionVerdict;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * What the losing team could see at an important fight (vision is stored for those only), as team
 * reasons. No ward is a reason only against a winner who had one, since vision both sides lacked
 * explains nothing; an invisible enemy hero without detection and a ward placed just before the
 * fight come from the losing team's own verdicts.
 */
@Component
@RequiredArgsConstructor
public class VisionReasonContributor implements LateReasonContributor {
  public static final String SOURCE = "VISION";
  static final String NO_VISION_BEFORE_FIGHT = "NO_VISION_BEFORE_FIGHT";
  static final String NO_DETECTION_VS_INVISIBLE = "NO_DETECTION_VS_INVISIBLE";
  static final String LATE_WARD = "LATE_WARD";

  private final ReasonProperties properties;
  private final HeroKits kits;

  @Override
  public String source() {
    return SOURCE;
  }

  @Override
  public boolean supports(GameModeProfile mode) {
    return true;
  }

  @Override
  public List<Reason> contribute(LateFightContext context) {
    TeamSide loser = context.fight().loser().orElse(null);
    FightVisionDomain own = loser == null ? null : context.vision(loser).orElse(null);
    if (own == null) {
      return List.of();
    }
    Set<VisionVerdict> verdicts = LateFightContext.verdicts(own);
    List<Reason> reasons = new ArrayList<>();
    if (verdicts.contains(VisionVerdict.NO_VISION)) {
      context
          .vision(other(loser))
          .filter(winner -> !LateFightContext.verdicts(winner).contains(VisionVerdict.NO_VISION))
          .ifPresent(
              winner ->
                  reasons.add(
                      team(
                          NO_VISION_BEFORE_FIGHT,
                          loser,
                          VisionVerdict.NO_VISION,
                          "%s had no ward covering the fight when it began, %s had %s"
                              .formatted(name(loser), name(other(loser)), wards(winner)))));
    }
    if (verdicts.contains(VisionVerdict.INVISIBLE_HERO_NO_DETECTION)) {
      String heroes = invisibleHeroes(context, own);
      reasons.add(
          team(
              NO_DETECTION_VS_INVISIBLE,
              loser,
              VisionVerdict.INVISIBLE_HERO_NO_DETECTION,
              "%s %s invisible in the fight and %s had no detection"
                  .formatted(heroes, heroes.contains(" and ") ? "were" : "was", name(loser))));
    }
    if (verdicts.contains(VisionVerdict.LATE_WARD)) {
      reasons.add(
          team(
              LATE_WARD,
              loser,
              VisionVerdict.LATE_WARD,
              own.getLateWardSeconds() == null
                  ? "%s's wards near the fight were placed just before it".formatted(name(loser))
                  : "%s's wards near the fight were placed %d s before it began"
                      .formatted(name(loser), own.getLateWardSeconds())));
    }
    return reasons;
  }

  private Reason team(String code, TeamSide team, VisionVerdict verdict, String evidence) {
    return Reason.team(SOURCE, code, team, properties.severity(verdict), evidence);
  }

  private String invisibleHeroes(LateFightContext context, FightVisionDomain own) {
    if (own.getInvisibleHeroes() == null || own.getInvisibleHeroes().isBlank()) {
      return "An enemy hero";
    }
    List<String> names =
        Arrays.stream(own.getInvisibleHeroes().split(","))
            .map(String::trim)
            .filter(slot -> slot.matches("\\d+"))
            .map(Long::parseLong)
            .map(
                slot ->
                    ModeReasonContributor.heroName(
                        ModeReasonContributor.hero(kits, context.fight(), slot), slot))
            .toList();
    return names.isEmpty() ? "An enemy hero" : String.join(" and ", names);
  }

  private static String wards(FightVisionDomain winner) {
    int observers = winner.getActiveObservers() == null ? 0 : winner.getActiveObservers();
    return observers == 1 ? "1 ward" : observers + " wards";
  }

  private static TeamSide other(TeamSide team) {
    return team == TeamSide.RADIANT ? TeamSide.DIRE : TeamSide.RADIANT;
  }

  private static String name(TeamSide team) {
    return team == TeamSide.RADIANT ? "Radiant" : "Dire";
  }
}
