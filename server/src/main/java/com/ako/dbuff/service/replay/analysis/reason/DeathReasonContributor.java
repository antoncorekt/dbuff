package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.DeathAnalysisDomain;
import com.ako.dbuff.service.replay.analysis.death.DeathReason;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Mirrors the reason the death analysis gave for each death in the fight, and who died first. */
@Component
@RequiredArgsConstructor
public class DeathReasonContributor implements ReasonContributor {
  static final String DIED_FIRST = "DIED_FIRST";
  private static final Set<DeathReason> NOT_A_REASON =
      Set.of(DeathReason.REINCARNATED, DeathReason.OTHER);

  private final ReasonProperties properties;

  @Override
  public boolean supports(GameModeProfile mode) {
    return true;
  }

  @Override
  public List<Reason> contribute(FightContext context) {
    List<DeathAnalysisDomain> deaths =
        context.deaths().stream().filter(d -> !Boolean.TRUE.equals(d.getReincarnated())).toList();
    List<Reason> reasons = new ArrayList<>();
    for (DeathAnalysisDomain death : deaths) {
      DeathReason reason = DeathReason.valueOf(death.getReason());
      if (!NOT_A_REASON.contains(reason)) {
        reasons.add(
            Reason.player(
                Reason.COMMON,
                reason.name(),
                death.getPlayerSlot(),
                properties.severity(reason),
                evidence(death)));
      }
    }
    if (deaths.size() >= properties.getDiedFirstMinDeaths()) {
      deaths.stream()
          .min(Comparator.comparing(DeathAnalysisDomain::getDeathSeconds))
          .ifPresent(
              first ->
                  reasons.add(
                      Reason.player(
                          Reason.COMMON,
                          DIED_FIRST,
                          first.getPlayerSlot(),
                          properties.getDiedFirstSeverity(),
                          evidence(first))));
    }
    return reasons;
  }

  private static String evidence(DeathAnalysisDomain death) {
    int second = (int) death.getDeathSeconds().doubleValue();
    String killer = death.getKillerName() == null ? death.getKillerKind() : death.getKillerName();
    return "died at %d:%02d to %s".formatted(second / 60, second % 60, killer);
  }
}
