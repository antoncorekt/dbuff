package com.ako.dbuff.service.replay.analysis.reason;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.TeamRotationDomain;
import com.ako.dbuff.service.replay.analysis.behaviour.RotationReason;
import com.ako.dbuff.service.replay.analysis.behaviour.RotationVerdict;
import com.ako.dbuff.service.replay.analysis.fight.Presence;
import com.ako.dbuff.service.replay.analysis.role.HeroKits;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A hero of the losing team whose rotation came to nothing (WASTED) or ended in his death, and
 * whose trip ended shortly before the fight or was still on when it began, and who then took no
 * real part in it: his team fought a man down for a trip that gave nothing.
 */
@Component
@RequiredArgsConstructor
public class RotationReasonContributor implements LateReasonContributor {
  public static final String SOURCE = "ROTATION";
  static final String ROTATION_WASTED_BEFORE_FIGHT = "ROTATION_WASTED_BEFORE_FIGHT";
  private static final Set<RotationVerdict> COUNTED =
      Set.of(RotationVerdict.WASTED, RotationVerdict.DIED);
  private static final int SECONDS_PER_MINUTE = 60;

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
  public List<Reason> contribute(LateFightContext late) {
    FightContext context = late.fight();
    FightDomain fight = context.fight();
    TeamSide loser = context.loser().orElse(null);
    if (loser == null || fight.getStartSeconds() == null) {
      return List.of();
    }
    double start = fight.getStartSeconds();
    double end = fight.getEndSeconds() == null ? start : fight.getEndSeconds();
    return late.rotations().stream()
        .filter(r -> loser.name().equals(r.getTeam()))
        .filter(r -> r.getPlayerSlot() != null)
        .filter(r -> r.getStartSeconds() != null && r.getEndSeconds() != null)
        .filter(r -> within(r, start, end))
        .filter(r -> !isActive(context.presenceOf(r.getPlayerSlot())))
        .map(r -> reason(context, r, start))
        .toList();
  }

  private boolean within(TeamRotationDomain rotation, double fightStart, double fightEnd) {
    RotationVerdict verdict = Enums.find(RotationVerdict.class, rotation.getVerdict());
    return COUNTED.contains(verdict)
        && rotation.getEndSeconds() >= fightStart - properties.getRotationWindowSeconds()
        && rotation.getStartSeconds() <= fightEnd;
  }

  private static boolean isActive(Presence presence) {
    return presence == Presence.ACTIVE;
  }

  private Reason reason(FightContext context, TeamRotationDomain rotation, double fightStart) {
    long slot = rotation.getPlayerSlot();
    RotationVerdict verdict = Enums.find(RotationVerdict.class, rotation.getVerdict());
    String hero =
        ModeReasonContributor.heroName(ModeReasonContributor.hero(kits, context, slot), slot);
    String timing =
        rotation.getEndSeconds() > fightStart
            ? "was still away when the fight began"
            : "ended %d s before the fight"
                .formatted((int) Math.round(fightStart - rotation.getEndSeconds()));
    String evidence =
        "%s left %s for %s at %s; the trip %s and %s"
            .formatted(
                hero,
                place(rotation.getFromZone(), rotation.getFromLane()),
                place(rotation.getToZone(), rotation.getToLane()),
                clock(rotation.getStartSeconds()),
                timing,
                outcome(verdict, rotation.getReasons()));
    return Reason.player(
        SOURCE,
        ROTATION_WASTED_BEFORE_FIGHT,
        slot,
        properties.byPresence(properties.severity(verdict), context.presenceOf(slot)),
        evidence);
  }

  private static String outcome(RotationVerdict verdict, String reasons) {
    if (verdict == RotationVerdict.DIED) {
      return "he died on it";
    }
    String words =
        reasons == null
            ? ""
            : Arrays.stream(reasons.split(","))
                .map(String::trim)
                .map(code -> Enums.find(RotationReason.class, code))
                .filter(r -> r != null)
                .map(RotationReason::words)
                .collect(Collectors.joining(" and "));
    return words.isEmpty() ? "gave nothing" : "gave nothing (" + words + ")";
  }

  private static String place(String zone, String lane) {
    if (lane != null) {
      return lane.toLowerCase(Locale.ROOT) + " lane";
    }
    return zone == null ? "his position" : "the " + zone.toLowerCase(Locale.ROOT);
  }

  private static String clock(int seconds) {
    return "%d:%02d".formatted(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE);
  }
}
