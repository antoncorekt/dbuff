package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.PlayerRoleDomain;
import com.ako.dbuff.dao.model.id.PlayerGameStatisticDomainId;
import com.ako.dbuff.dao.repo.ItemRepository;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.PlayerRoleRepo;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * What else the same gold usually bought: the items the same hero in the same role bought close in
 * time and cost to a purchase, from the stored matches of all players over the last 12 months, with
 * their win rate and net worth lead against the item's own.
 */
@Component
public class AlternativeFinder {
  static final int LEAD_MINUTES = 10;

  private static final double DEFAULT_COST_BAND = 0.3;
  private static final double DEFAULT_TIME_BAND = 300;
  private static final double DEFAULT_MIN_GAMES = 5;
  private static final Duration WINDOW = Duration.ofDays(365);
  private static final Map<Integer, Long> LANE_OF_ROLE = Map.of(1, 1L, 2, 2L, 3, 3L, 4, 4L);
  private static final Map<Long, Integer> ROLE_OF_LANE = Map.of(1L, 1, 2L, 2, 3L, 3, 4L, 4);

  /**
   * @param costBand how far a candidate's cost may be from the purchase's, as a fraction of it
   * @param timeBand how far in seconds a purchase may be from this one
   * @param minGames the games both sides need before the comparison is shown
   */
  public record Settings(double costBand, int timeBand, int minGames) {
    public static Settings of(Map<String, Double> values) {
      return new Settings(
          values.getOrDefault("altCostBand", DEFAULT_COST_BAND),
          values.getOrDefault("altTimeBand", DEFAULT_TIME_BAND).intValue(),
          values.getOrDefault("altMinGames", DEFAULT_MIN_GAMES).intValue());
    }
  }

  /**
   * @param ownItems the keys of the entry's items, never offered as their own alternative
   * @param fixed the keys the catalog names as the candidates; empty when the data picks them
   */
  public record Purchase(
      long matchId,
      long slot,
      String itemKey,
      Set<String> ownItems,
      double seconds,
      long cost,
      List<String> fixed) {}

  /**
   * @param winRateDelta the candidate's win rate minus the item's; null with too few games
   * @param leadDelta the candidate's average net worth lead minus the item's; null when either side
   *     lacks the timeline or has too few games
   */
  public record Alternative(String itemKey, long games, Double winRateDelta, Double leadDelta) {}

  /** The item's own games and the candidates, those with most games first. */
  public record Result(long games, List<Alternative> alternatives) {
    static final Result NONE = new Result(0, List.of());
  }

  private record Tally(long games, long wins, double leadSum, long leads) {
    double winRate() {
      return games == 0 ? 0 : (double) wins / games;
    }

    Double lead() {
      return leads == 0 ? null : leadSum / leads;
    }
  }

  private final ItemRepository itemRepo;
  private final PlayerGameStatisticRepo statRepo;
  private final PlayerRoleRepo roleRepo;
  private final ItemKits kits;
  private final Clock clock;

  @Autowired
  public AlternativeFinder(
      ItemRepository itemRepo,
      PlayerGameStatisticRepo statRepo,
      PlayerRoleRepo roleRepo,
      ItemKits kits) {
    this(itemRepo, statRepo, roleRepo, kits, Clock.systemUTC());
  }

  AlternativeFinder(
      ItemRepository itemRepo,
      PlayerGameStatisticRepo statRepo,
      PlayerRoleRepo roleRepo,
      ItemKits kits,
      Clock clock) {
    this.itemRepo = itemRepo;
    this.statRepo = statRepo;
    this.roleRepo = roleRepo;
    this.kits = kits;
    this.clock = clock;
  }

  public Result find(Purchase purchase, Settings settings) {
    Optional<PlayerMatchStatisticDomain> player =
        statRepo.findById(new PlayerGameStatisticDomainId(purchase.matchId(), purchase.slot()));
    OptionalLong subjectId =
        kits.item(purchase.itemKey()).map(i -> i.getId()).stream()
            .filter(java.util.Objects::nonNull)
            .mapToLong(Long::longValue)
            .findFirst();
    if (player.isEmpty() || player.get().getHeroId() == null || subjectId.isEmpty()) {
      return Result.NONE;
    }
    Integer role = role(purchase, player.get());
    if (role == null) {
      return Result.NONE;
    }
    List<Object[]> rows =
        itemRepo.findHeroPurchases(
            player.get().getHeroId(),
            role,
            LANE_OF_ROLE.get(role),
            Math.round(purchase.seconds() - settings.timeBand()),
            Math.round(purchase.seconds() + settings.timeBand()),
            LEAD_MINUTES,
            purchase.matchId(),
            clock.instant().minus(WINDOW).getEpochSecond());
    Map<Long, Tally> tallies = tally(rows);
    Tally own = tallies.getOrDefault(subjectId.getAsLong(), new Tally(0, 0, 0, 0));
    Set<Long> candidates = candidates(purchase, settings, tallies.keySet());
    List<Alternative> alternatives = new ArrayList<>();
    for (long id : candidates) {
      Tally other = tallies.get(id);
      kits.key(id)
          .ifPresent(key -> alternatives.add(alternative(key, other, own, settings.minGames())));
    }
    alternatives.sort(
        Comparator.comparingLong(Alternative::games)
            .reversed()
            .thenComparing(Alternative::itemKey));
    return new Result(own.games(), List.copyOf(alternatives));
  }

  private Integer role(Purchase purchase, PlayerMatchStatisticDomain player) {
    Optional<Integer> stored =
        roleRepo.findByMatchId(purchase.matchId()).stream()
            .filter(r -> r.getPlayerSlot() != null && r.getPlayerSlot() == purchase.slot())
            .map(PlayerRoleDomain::getRole)
            .filter(java.util.Objects::nonNull)
            .findFirst();
    return stored.orElseGet(
        () -> player.getLaneRole() == null ? null : ROLE_OF_LANE.get(player.getLaneRole()));
  }

  private Set<Long> candidates(Purchase purchase, Settings settings, Collection<Long> bought) {
    Set<Long> ids = new HashSet<>();
    for (long id : bought) {
      Optional<String> key = kits.key(id);
      if (key.isEmpty() || purchase.ownItems().contains(key.get())) {
        continue;
      }
      boolean chosen =
          purchase.fixed().isEmpty()
              ? withinCostBand(key.get(), purchase.cost(), settings.costBand())
              : purchase.fixed().contains(key.get());
      if (chosen) {
        ids.add(id);
      }
    }
    return ids;
  }

  private boolean withinCostBand(String key, long cost, double band) {
    OptionalLong other = kits.cost(key);
    return other.isPresent() && Math.abs(other.getAsLong() - cost) <= band * cost;
  }

  private static Alternative alternative(String key, Tally other, Tally own, int minGames) {
    if (other.games() < minGames || own.games() < minGames) {
      return new Alternative(key, other.games(), null, null);
    }
    Double lead = other.lead() == null || own.lead() == null ? null : other.lead() - own.lead();
    return new Alternative(key, other.games(), other.winRate() - own.winRate(), lead);
  }

  private static Map<Long, Tally> tally(List<Object[]> rows) {
    Map<Long, Tally> tallies = new HashMap<>();
    for (Object[] row : rows) {
      long id = ((Number) row[0]).longValue();
      boolean won = row[2] != null && ((Number) row[2]).longValue() == 1;
      Double lead = row[3] == null ? null : ((Number) row[3]).doubleValue();
      tallies.merge(
          id,
          new Tally(1, won ? 1 : 0, lead == null ? 0 : lead, lead == null ? 0 : 1),
          (a, b) ->
              new Tally(
                  a.games() + b.games(),
                  a.wins() + b.wins(),
                  a.leadSum() + b.leadSum(),
                  a.leads() + b.leads()));
    }
    return tallies;
  }
}
