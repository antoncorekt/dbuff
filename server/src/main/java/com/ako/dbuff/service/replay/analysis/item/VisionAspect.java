package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.WardEventDomain;
import com.ako.dbuff.dao.model.WardKind;
import com.ako.dbuff.dao.model.WardRemovalKind;
import com.ako.dbuff.service.replay.PlayerSlots;
import com.ako.dbuff.service.replay.StateChangeKind;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import com.ako.dbuff.service.replay.analysis.vision.VisionProperties;
import com.ako.dbuff.service.replay.analysis.vision.WardCoordinates;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * What the vision items gave: the facts of the hero's wards, sentries, dust or gem, and a gold
 * value of the invisible enemies killed where the item gave sight (a share of the kill, the same
 * fixed share a disable earns) and of the enemy wards his team removed while his sentry was up (the
 * deward bounty). Observers have no kill of their own: their facts and flags are the verdict. With
 * less than {@code minVisionGold} spent on the entry's items there is no ratio.
 */
@Component
@RequiredArgsConstructor
public class VisionAspect implements ItemAspect {
  public static final String NAME = "vision";

  static final String NEVER_PLACED = "NEVER_PLACED";
  static final String DUST_UNUSED = "DUST_UNUSED";
  static final String DEWARDED_FAST = "DEWARDED_FAST";
  static final String INVISIBLE_KILL = "INVISIBLE_KILL";
  static final String DEWARD = "DEWARD";

  private static final String OBSERVER = "item_ward_observer";
  private static final String SENTRY = "item_ward_sentry";
  private static final String DUST = "item_dust";
  private static final String GEM = "item_gem";
  private static final Set<String> PARAMETERS = parameters();

  private final VisionProperties properties;

  private static Set<String> parameters() {
    Set<String> names = new HashSet<>(WorthPrices.NAMES);
    names.addAll(
        Set.of(
            "dewardBounty",
            "dewardFastSeconds",
            "minVisionGold",
            "invisibleSeconds",
            "dustSeconds",
            "dustRadius",
            "gemRadius"));
    return Set.copyOf(names);
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public Set<String> parameterNames() {
    return PARAMETERS;
  }

  @Override
  public AspectPart measure(AspectContext context) {
    HeroInputs hero = context.hero();
    VisionFacts vision = hero.match().vision();
    Set<String> names = Set.copyOf(context.names());
    boolean wardLog = !vision.wards().isEmpty();
    List<WardEventDomain> mine =
        vision.wards().stream()
            .filter(w -> Long.valueOf(hero.slot()).equals(w.getPlayerSlot()))
            .toList();
    long bought = purchases(hero, names);
    double spent = bought * (context.cost() == null ? 0 : context.cost());
    Map<String, Double> figures = new LinkedHashMap<>();
    List<String> flags = new ArrayList<>();
    List<Detail> rows = new ArrayList<>();
    figures.put("visionBought", (double) bought);

    WardKind kind = names.contains(OBSERVER) ? WardKind.OBSERVER : WardKind.SENTRY;
    boolean isWard = names.contains(OBSERVER) || names.contains(SENTRY);
    List<WardEventDomain> placed =
        isWard ? mine.stream().filter(w -> w.getWardKind() == kind).toList() : List.of();
    if (isWard && wardLog) {
      figures.put("visionPlaced", (double) placed.size());
      if (bought > 0 && placed.isEmpty()) {
        flags.add(NEVER_PLACED);
      }
    }
    if (names.contains(OBSERVER)) {
      observerFacts(context, placed, figures, flags);
    }
    if (names.contains(DUST)) {
      int uses = vision.detection().dustUsesOf(hero.slot()).size();
      figures.put("visionDustUses", (double) uses);
      if (bought > 0 && uses == 0) {
        flags.add(DUST_UNUSED);
      }
    }
    double killShare = 0;
    double bounty = 0;
    int kills = 0;
    int dewards = 0;
    if (!names.contains(OBSERVER)) {
      KillShare shares = new KillShare(hero, WorthPrices.of(context));
      double share = WorthPrices.of(context).disableShare();
      for (KillShare.Death death : shares.deaths()) {
        if (revealed(context, names, placed, death)) {
          kills++;
          killShare += death.value() * share;
          rows.add(
              new Detail(
                  INVISIBLE_KILL,
                  death.second(),
                  death.victim(),
                  Map.of("killValue", death.value(), "killShare", death.value() * share)));
        }
      }
    }
    if (names.contains(SENTRY)) {
      double each = context.entry().parameter("dewardBounty", 50);
      for (WardEventDomain ward : dewardsWhileUp(hero, placed)) {
        dewards++;
        bounty += each;
        rows.add(
            new Detail(
                DEWARD, ward.getRemovedTime(), ward.getWardKind().name(), Map.of("bounty", each)));
      }
    }
    if (!names.contains(OBSERVER)) {
      figures.put("visionInvisibleKills", (double) kills);
      figures.put("visionKillShare", killShare);
    }
    if (names.contains(SENTRY)) {
      figures.put("visionDewards", (double) dewards);
      figures.put("visionDewardBounty", bounty);
    }
    double gain = killShare + bounty;
    figures.put(AspectSum.SPENT, spent);
    figures.put(DamageAspects.GAIN + NAME, gain);
    boolean enough = spent >= context.entry().parameter("minVisionGold", 300);
    return new AspectPart(gain, enough, figures, flags, rows, (int) bought, null, null);
  }

  private void observerFacts(
      AspectContext context,
      List<WardEventDomain> observers,
      Map<String, Double> figures,
      List<String> flags) {
    double fastLimit = context.entry().parameter("dewardFastSeconds", 60);
    double lifeSum = 0;
    int lives = 0;
    int fast = 0;
    for (WardEventDomain w : observers) {
      if (w.getPlacedTime() == null || w.getRemovedTime() == null) {
        continue;
      }
      double life = w.getRemovedTime() - w.getPlacedTime();
      if (w.getRemovalKind() == WardRemovalKind.UNKNOWN) {
        continue;
      }
      lifeSum += life;
      lives++;
      if (w.getRemovalKind() == WardRemovalKind.KILLED && life < fastLimit) {
        fast++;
      }
    }
    if (lives > 0) {
      figures.put("visionObserverLifeSeconds", lifeSum / lives);
    }
    figures.put("visionDewardedFast", (double) fast);
    if (fast > 0) {
      flags.add(DEWARDED_FAST);
    }
  }

  /** Whether the enemy was invisible just before dying and one of the hero's items gave sight. */
  private boolean revealed(
      AspectContext context, Set<String> names, List<WardEventDomain> sentries, KillShare.Death d) {
    HeroInputs hero = context.hero();
    VisionFacts vision = hero.match().vision();
    Long victim = hero.match().slots().get(d.victim());
    if (victim == null) {
      return false;
    }
    double seconds = context.entry().parameter("invisibleSeconds", 5);
    if (vision
        .invisibility()
        .invisibleIn(Set.of(victim), d.second() - seconds, d.second(), 0, vision.abilities())
        .isEmpty()) {
      return false;
    }
    double[] at = vision.positionOf(victim, d.second());
    if (at == null) {
      return false;
    }
    return (names.contains(SENTRY) && underSentry(sentries, d.second(), at))
        || (names.contains(DUST) && dustReveal(context, d, victim))
        || (names.contains(GEM) && gemNearby(context, d.second(), at));
  }

  private boolean underSentry(List<WardEventDomain> sentries, double second, double[] at) {
    double reach = properties.getSentryRadius() + properties.getWardPositionTolerance();
    return sentries.stream()
        .filter(w -> alive(w, second) && w.getX() != null && w.getY() != null)
        .anyMatch(w -> WardCoordinates.distance(w.getX(), w.getY(), at[0], at[1]) <= reach);
  }

  private boolean dustReveal(AspectContext context, KillShare.Death d, long victim) {
    HeroInputs hero = context.hero();
    VisionFacts vision = hero.match().vision();
    double window = context.entry().parameter("dustSeconds", 12);
    double radius = context.entry().parameter("dustRadius", 1050);
    for (double use : vision.detection().dustUsesOf(hero.slot())) {
      if (use > d.second() || d.second() - use > window) {
        continue;
      }
      double[] user = vision.positionOf(hero.slot(), use);
      double[] target = vision.positionOf(victim, use);
      if (user != null
          && target != null
          && Math.hypot(user[0] - target[0], user[1] - target[1]) <= radius) {
        return true;
      }
    }
    return false;
  }

  private boolean gemNearby(AspectContext context, double second, double[] at) {
    HeroInputs hero = context.hero();
    VisionFacts vision = hero.match().vision();
    if (!vision.detection().held(Set.of(hero.slot()), second).contains("gem")) {
      return false;
    }
    double[] holder = vision.positionOf(hero.slot(), second);
    return holder != null
        && Math.hypot(holder[0] - at[0], holder[1] - at[1])
            <= context.entry().parameter("gemRadius", 900);
  }

  /** Enemy wards the hero's team destroyed while one of his sentries covered them. */
  private List<WardEventDomain> dewardsWhileUp(HeroInputs hero, List<WardEventDomain> sentries) {
    double reach = properties.getSentryRadius() + properties.getWardPositionTolerance();
    boolean radiant = PlayerSlots.radiant(hero.slot());
    return hero.match().vision().wards().stream()
        .filter(w -> w.getPlayerSlot() != null && PlayerSlots.radiant(w.getPlayerSlot()) != radiant)
        .filter(w -> w.getRemovalKind() == WardRemovalKind.KILLED && w.getRemovedTime() != null)
        .filter(w -> w.getX() != null && w.getY() != null)
        .filter(w -> ownTeam(hero, w.getRemovedBy()))
        .filter(
            w ->
                sentries.stream()
                    .anyMatch(
                        s ->
                            alive(s, w.getRemovedTime())
                                && s.getX() != null
                                && s.getY() != null
                                && WardCoordinates.distance(
                                        w.getX(),
                                        w.getY(),
                                        WardCoordinates.toWorld(s.getX()),
                                        WardCoordinates.toWorld(s.getY()))
                                    <= reach))
        .toList();
  }

  private static boolean ownTeam(HeroInputs hero, String remover) {
    Long slot = hero.match().slots().get(remover);
    return slot != null && PlayerSlots.radiant(slot) == PlayerSlots.radiant(hero.slot());
  }

  private static boolean alive(WardEventDomain w, double second) {
    return w.getPlacedTime() != null
        && w.getWardKind() != null
        && w.getPlacedTime() <= second
        && second
            < (w.getRemovedTime() != null
                ? w.getRemovedTime()
                : w.getPlacedTime() + w.getWardKind().getLifetimeSeconds());
  }

  /**
   * The adds of the items; a gem dropped at a death and picked up again is not bought twice, so a
   * gem counts once.
   */
  private static long purchases(HeroInputs hero, Set<String> names) {
    long adds =
        hero.changes().stream()
            .filter(c -> StateChangeKind.ITEM_ADDED.name().equals(c.getKind()))
            .filter(c -> names.contains(c.getName()))
            .count();
    return names.contains(GEM) ? Math.min(1, adds) : adds;
  }
}
