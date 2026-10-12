package com.ako.dbuff.service.replay;

/**
 * One change in what a hero has or can do, seen at a sample.
 *
 * <p>The starting inventory shows up as items added at the first sample, before the horn. An item
 * removed has no slot, so its {@code slotIndex} is -1.
 *
 * <p>Changes are noticed on the sampling grid, so a time is accurate to a few seconds, but a
 * cooldown carries the moment it ends exactly: the replay stores the time remaining, and the sample
 * time plus that is when the ability is ready again.
 *
 * @param replayPlayerId the replay's player id
 * @param gameSecond seconds from 0:00 at which the change was seen
 * @param name the game's internal item or ability name
 * @param slotIndex inventory slot for an item, ability slot for an ability, -1 when unknown
 * @param readyAtSeconds for a cooldown, the second it ends; otherwise 0
 * @param cooldownLength for a cooldown, its full length in seconds; 0 when the replay does not say,
 *     and -1 for a change that ends a cooldown early (a reset), whose {@code readyAtSeconds} is the
 *     moment it was seen to be over (some abilities leave it unset)
 * @param level for an ability level change, the new level; otherwise 0
 * @param manaCost for a mana cost change, the new cost; otherwise 0
 */
public record StateChange(
    int replayPlayerId,
    double gameSecond,
    StateChangeKind kind,
    String name,
    int slotIndex,
    double readyAtSeconds,
    double cooldownLength,
    int level,
    int manaCost) {}
