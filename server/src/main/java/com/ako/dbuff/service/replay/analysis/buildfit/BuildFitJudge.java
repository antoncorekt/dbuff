package com.ako.dbuff.service.replay.analysis.buildfit;

import com.ako.dbuff.service.replay.analysis.role.FunctionProfile;
import com.ako.dbuff.service.replay.analysis.role.RoleFitter;
import com.ako.dbuff.service.replay.analysis.role.RoleFunction;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Compares the functions of a drafted build with what its role needs. A function the build does not
 * know is reported as unknown, never as fitting; a missing function is only a stated gap where the
 * match backs it: no escape and deaths alone, no farm skill and a low net worth place.
 */
@Component
@RequiredArgsConstructor
public class BuildFitJudge {
  private final RoleNeeds needs;
  private final BuildFitProperties properties;

  /**
   * What the match shows about the player.
   *
   * @param lonelyDeaths deaths with no ally in range
   * @param netWorthPlace place in the team by net worth, 1 the highest; NaN when unknown
   */
  public record Evidence(int lonelyDeaths, double netWorthPlace) {}

  /**
   * @param missing every need the build lacks
   * @param gaps the missing functions the match backs
   * @param unknown the wanted functions the build has no value for
   */
  public record Verdict(
      List<RoleFunction> missing, List<BuildGap> gaps, List<RoleFunction> unknown) {
    public BuildFitVerdict level() {
      if (!gaps.isEmpty()) {
        return BuildFitVerdict.GAP;
      }
      return missing.isEmpty() ? BuildFitVerdict.FIT : BuildFitVerdict.GAPS_NOT_SHOWN;
    }
  }

  /**
   * The escape minimum comes from {@link BuildFitProperties#getEscapeBonusMinimum()} for every role
   * and nowhere else, so a role-needs entry naming ESCAPE would be a second, conflicting source and
   * stops start-up.
   */
  @PostConstruct
  void validate() {
    for (int role = 1; role <= RoleFitter.ROLES; role++) {
      if (needs.forRole(role).containsKey(RoleFunction.ESCAPE)) {
        throw new IllegalStateException(
            "Role needs: role "
                + role
                + " names ESCAPE; the escape minimum is"
                + " replay.analysis.build-fit.escape-bonus-minimum");
      }
    }
  }

  public Verdict judge(FunctionProfile build, int role, Evidence evidence) {
    Map<RoleFunction, Double> wanted = new EnumMap<>(RoleFunction.class);
    wanted.put(RoleFunction.ESCAPE, properties.getEscapeBonusMinimum());
    wanted.putAll(needs.forRole(role));
    List<RoleFunction> missing = new ArrayList<>();
    List<BuildGap> gaps = new ArrayList<>();
    List<RoleFunction> unknown = new ArrayList<>();
    wanted.forEach(
        (function, minimum) -> {
          Double value = build.of(function);
          if (value == null) {
            unknown.add(function);
            return;
          }
          if (value >= minimum) {
            return;
          }
          missing.add(function);
          backed(function, value == 0, evidence).ifPresent(gaps::add);
        });
    return new Verdict(missing, gaps, unknown);
  }

  private Optional<BuildGap> backed(RoleFunction function, boolean none, Evidence evidence) {
    if (function == RoleFunction.ESCAPE
        && evidence.lonelyDeaths() >= properties.getMinLonelyDeaths()) {
      return Optional.of(
          new BuildGap(function, none, BuildGap.Evidence.DIED_ALONE, evidence.lonelyDeaths()));
    }
    if (function == RoleFunction.FARM_PRIORITY
        && evidence.netWorthPlace() >= properties.getLowNetWorthPlace()) {
      return Optional.of(
          new BuildGap(function, none, BuildGap.Evidence.LOW_NET_WORTH, evidence.netWorthPlace()));
    }
    return Optional.empty();
  }
}
