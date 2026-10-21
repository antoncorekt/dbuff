package com.ako.dbuff.service.replay.analysis.skill;

import com.ako.dbuff.dao.model.FightDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.replay.CastSnapshot;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.analysis.health.HeroHealth;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * What a skill aspect reads about one caster and one entry.
 *
 * @param match what is stored about the whole match
 * @param entry the catalog entry being judged
 * @param caster the hero who has the skill
 * @param casts his casts of the skill
 */
public record SkillContext(Match match, SkillCatalogEntry entry, Hero caster, SkillCasts casts) {
  private static final int ALIVE_FLAG = 1;

  /**
   * @param slot the player slot
   * @param playerId the replay player id; the snapshots and the combat log's assist bits count
   *     players by it, and {@code playerId / 2} is the player index
   * @param name the hero's name in the combat log
   * @param team the replay team number
   * @param track null when the hero has none
   * @param health his health, from the track and the combat log
   */
  public record Hero(
      long slot,
      int playerId,
      String name,
      int team,
      ReplayPlayerTrackDomain track,
      HeroHealth health) {

    public int playerIndex() {
      return playerId / 2;
    }

    private int sampleAt(double second) {
      if (track == null
          || track.getStartSeconds() == null
          || track.getStepSeconds() == null
          || track.getStepSeconds() <= 0
          || track.getSampleCount() == null
          || track.getX() == null
          || track.getY() == null) {
        return -1;
      }
      int index = (int) Math.floor((second - track.getStartSeconds()) / track.getStepSeconds());
      return index < 0 || index >= track.getSampleCount() || index >= track.getX().length
          ? -1
          : index;
    }

    /** His position at the last track sample at or before the second; empty when unknown. */
    public Optional<int[]> positionAt(double second) {
      int index = sampleAt(second);
      return index < 0
          ? Optional.empty()
          : Optional.of(new int[] {track.getX()[index], track.getY()[index]});
    }

    /**
     * False only when the last sample says dead or a death came after it; unknown counts as alive.
     */
    public boolean aliveAt(double second) {
      int index = sampleAt(second);
      if (index < 0 || track.getFlags() == null || index >= track.getFlags().length) {
        return true;
      }
      double sampleSecond = track.getStartSeconds() + index * (double) track.getStepSeconds();
      return (track.getFlags()[index] & ALIVE_FLAG) != 0
          && !health.diedBetween(sampleSecond, second);
    }
  }

  /**
   * @param heroes every hero of the roster; a hero can be missing from a snapshot frame, so
   *     snapshot code never assumes ten
   * @param events the match's combat log
   * @param fights the match's fights
   * @param abilities the constants of an ability by name
   * @param snapshots the cast snapshots, read on first use
   */
  public record Match(
      List<Hero> heroes,
      List<CombatEvent> events,
      List<FightDomain> fights,
      Function<String, Optional<AbilityConstant>> abilities,
      Supplier<List<CastSnapshot>> snapshots) {

    public Optional<Hero> hero(String name) {
      return heroes.stream().filter(h -> h.name().equals(name)).findFirst();
    }
  }

  public double parameter(String name, double fallback) {
    return entry.parameter(name, fallback);
  }

  /**
   * One number of the ability's attributes for the level (the last value when the level is past the
   * list); empty when the constants lack it or it is not a number.
   */
  public OptionalDouble attribute(String ability, String key, int level) {
    return match.abilities().apply(ability).map(AbilityConstant::getAttrib).stream()
        .flatMap(List::stream)
        .filter(a -> key.equals(a.getKey()) && a.getValue() != null && !a.getValue().isEmpty())
        .findFirst()
        .map(a -> a.getValue().get(Math.min(Math.max(level, 1), a.getValue().size()) - 1))
        .map(SkillContext::number)
        .orElse(OptionalDouble.empty());
  }

  private static OptionalDouble number(String text) {
    try {
      return OptionalDouble.of(Double.parseDouble(text.replaceAll("[^0-9.\\-]", "")));
    } catch (NumberFormatException e) {
      return OptionalDouble.empty();
    }
  }
}
