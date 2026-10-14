package com.ako.dbuff.service.replay.analysis.spike;

import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.service.replay.StateChangeKind;
import com.ako.dbuff.service.replay.analysis.item.HeroInputs;
import com.ako.dbuff.service.replay.analysis.item.ItemKits;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Names the item completion that most plausibly moved a team's strength just before a swing: the
 * most expensive item a hero of the favoured team first got in the window before the swing, bought
 * before the swing's first fight, by a hero who was active in one of its fights.
 *
 * <p>Completions come from the replay's item state changes, not from OpenDota's purchase log: they
 * exist for every analysed match and share the fights' clock, while the log is missing for a match
 * OpenDota has not parsed. Only a gain that looks like a completion counts (see {@link
 * #completions}), so moving an item between inventory, backpack and stash is not a completion. The
 * time is accurate to a few seconds. A match without state changes simply has no key items.
 */
@Component
@RequiredArgsConstructor
public class KeyItemFinder {
  private static final int REGAIN_SECONDS = 60;
  private static final String RECIPE_PREFIX = HeroInputs.replayName("recipe_");
  private static final Pattern TIER = Pattern.compile("(.+?)_([2-5])");

  private final ItemKits kits;
  private final SpikeProperties properties;

  /** An item a hero got, its internal name without the item prefix, and its cost. */
  public record Completion(long playerSlot, String item, double seconds, long cost) {}

  /** The swing the key item is looked for: its start, the favoured team and its fights. */
  public record Swing(int startSeconds, TeamSide team, List<FightActivity> fights) {}

  /** A fight of the swing, when it began and which heroes took an active part. */
  public record FightActivity(double startSeconds, Set<Long> activeSlots) {}

  private record Key(long slot, String name) {}

  /** What one hero did with one item so far: how many he holds and when he last let go of all. */
  private static final class Holding {
    private int held;
    private double lastEmpty = Double.NEGATIVE_INFINITY;
    private boolean everHeld;
  }

  /**
   * Every gain that looks like a completion of an item that costs at least the minimum. A gain
   * counts when it is the hero's first of the item, or he let go of all of it more than {@value
   * #REGAIN_SECONDS} seconds before (sold and bought again), so moving an item between inventory,
   * backpack and stash does not. It does not count when a teammate lost the same item within that
   * time (a trade, or a drop picked up), for a recipe, an item on the exclusion list, or a tier
   * above the first (<code>_2</code> to <code>_5</code>) of an item the hero held a lower tier of.
   * Only the hero's own earlier tiers are checked, not whether the lower tier came from a teammate.
   */
  public List<Completion> completions(List<ReplayStateChangeDomain> changes) {
    List<ReplayStateChangeDomain> ordered =
        changes.stream()
            .filter(c -> c.getPlayerSlot() != null && c.getGameSeconds() != null)
            .filter(c -> c.getName() != null && c.getName().startsWith(HeroInputs.ITEM_PREFIX))
            .sorted(Comparator.comparingDouble(ReplayStateChangeDomain::getGameSeconds))
            .toList();
    Map<String, List<ReplayStateChangeDomain>> removals = new HashMap<>();
    ordered.stream()
        .filter(c -> StateChangeKind.ITEM_REMOVED.name().equals(c.getKind()))
        .forEach(c -> removals.computeIfAbsent(c.getName(), k -> new ArrayList<>()).add(c));
    Map<Key, Holding> holdings = new HashMap<>();
    List<Completion> found = new ArrayList<>();
    for (ReplayStateChangeDomain c : ordered) {
      Holding holding =
          holdings.computeIfAbsent(new Key(c.getPlayerSlot(), c.getName()), k -> new Holding());
      if (StateChangeKind.ITEM_REMOVED.name().equals(c.getKind())) {
        if (holding.held > 0 && --holding.held == 0) {
          holding.lastEmpty = c.getGameSeconds();
        }
      } else if (StateChangeKind.ITEM_ADDED.name().equals(c.getKind())) {
        boolean fresh =
            !holding.everHeld
                || holding.held == 0 && c.getGameSeconds() - holding.lastEmpty > REGAIN_SECONDS;
        boolean earlierTier = ownedLowerTier(holdings, c);
        holding.held++;
        holding.everHeld = true;
        if (fresh
            && !earlierTier
            && !properties.excludes(c.getName())
            && !c.getName().startsWith(RECIPE_PREFIX)
            && !passedFromTeammate(removals, c)) {
          completion(c).ifPresent(found::add);
        }
      }
    }
    return found;
  }

  private boolean ownedLowerTier(Map<Key, Holding> holdings, ReplayStateChangeDomain c) {
    Matcher tier = TIER.matcher(c.getName());
    if (!tier.matches()) {
      return false;
    }
    int level = Integer.parseInt(tier.group(2));
    for (int lower = 1; lower < level; lower++) {
      String name = tier.group(1) + (lower == 1 ? "" : "_" + lower);
      Holding earlier = holdings.get(new Key(c.getPlayerSlot(), name));
      if (earlier != null && earlier.everHeld) {
        return true;
      }
    }
    return false;
  }

  private static boolean passedFromTeammate(
      Map<String, List<ReplayStateChangeDomain>> removals, ReplayStateChangeDomain gain) {
    TeamSide team = TeamSide.ofSlot(gain.getPlayerSlot());
    return removals.getOrDefault(gain.getName(), List.of()).stream()
        .anyMatch(
            r ->
                !r.getPlayerSlot().equals(gain.getPlayerSlot())
                    && TeamSide.ofSlot(r.getPlayerSlot()) == team
                    && r.getGameSeconds() <= gain.getGameSeconds()
                    && gain.getGameSeconds() - r.getGameSeconds() <= REGAIN_SECONDS);
  }

  public Optional<Completion> find(Collection<Completion> completions, Swing swing) {
    OptionalDouble firstFight =
        swing.fights().stream().mapToDouble(FightActivity::startSeconds).min();
    if (firstFight.isEmpty()) {
      return Optional.empty();
    }
    Set<Long> active = new HashSet<>();
    swing.fights().forEach(f -> active.addAll(f.activeSlots()));
    double from = swing.startSeconds() - properties.getKeyItemWindowSeconds();
    return completions.stream()
        .filter(
            c -> TeamSide.ofSlot(c.playerSlot()) == swing.team() && active.contains(c.playerSlot()))
        .filter(c -> c.seconds() >= from && c.seconds() <= swing.startSeconds())
        .filter(c -> c.seconds() < firstFight.getAsDouble())
        .min(
            Comparator.comparingLong(Completion::cost)
                .reversed()
                .thenComparingDouble(Completion::seconds)
                .thenComparingLong(Completion::playerSlot));
  }

  private Optional<Completion> completion(ReplayStateChangeDomain change) {
    String item = HeroInputs.itemKey(change.getName());
    OptionalLong cost = kits.cost(item);
    if (cost.isEmpty() || cost.getAsLong() < properties.getKeyItemMinCost()) {
      return Optional.empty();
    }
    return Optional.of(
        new Completion(change.getPlayerSlot(), item, change.getGameSeconds(), cost.getAsLong()));
  }
}
