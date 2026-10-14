package com.ako.dbuff.service.replay.analysis.spike;

import com.ako.dbuff.dao.model.ReplayStateChangeDomain;
import com.ako.dbuff.service.replay.StateChangeKind;
import com.ako.dbuff.service.replay.analysis.item.ItemKits;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Names the item completion that most plausibly moved a team's strength just before a swing: the
 * most expensive item a hero of the favoured team first got in the window before the swing, bought
 * before the swing's first fight, by a hero who was active in one of its fights.
 *
 * <p>Completions come from the replay's item state changes, not from OpenDota's purchase log: they
 * exist for every analysed match and share the fights' clock, while the log is missing for a match
 * OpenDota has not parsed. A hero's first gain of an item counts, so moving an item between
 * inventory, backpack and stash is not a completion. The time is accurate to a few seconds. A match
 * without state changes simply has no key items.
 */
@Component
@RequiredArgsConstructor
public class KeyItemFinder {
  private final ItemKits kits;
  private final SpikeProperties properties;

  /** An item a hero got, its internal name without the item prefix, and its cost. */
  public record Completion(long playerSlot, String item, double seconds, long cost) {}

  /** The swing the key item is looked for: its start, the favoured team and its fights. */
  public record Swing(int startSeconds, TeamSide team, List<FightActivity> fights) {}

  /** A fight of the swing, when it began and which heroes took an active part. */
  public record FightActivity(double startSeconds, Set<Long> activeSlots) {}

  /** Every hero's first gain of an item that costs at least the minimum. */
  public List<Completion> completions(List<ReplayStateChangeDomain> changes) {
    Set<String> seen = new HashSet<>();
    return changes.stream()
        .filter(c -> StateChangeKind.ITEM_ADDED.name().equals(c.getKind()))
        .filter(c -> c.getPlayerSlot() != null && c.getGameSeconds() != null && c.getName() != null)
        .sorted(Comparator.comparingDouble(ReplayStateChangeDomain::getGameSeconds))
        .filter(c -> seen.add(c.getPlayerSlot() + c.getName()))
        .map(this::completion)
        .flatMap(Optional::stream)
        .toList();
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
    String item =
        change.getName().startsWith("item_") ? change.getName().substring(5) : change.getName();
    OptionalLong cost = kits.cost(item);
    if (cost.isEmpty() || cost.getAsLong() < properties.getKeyItemMinCost()) {
      return Optional.empty();
    }
    return Optional.of(
        new Completion(change.getPlayerSlot(), item, change.getGameSeconds(), cost.getAsLong()));
  }
}
