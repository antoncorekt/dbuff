package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
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
  private static final int MID_ROLE = 2;
  private static final long MID_LANE = 2;
  private static final int CACHED = 64;

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
  private final Map<Long, MatchFacts> matches = lru();
  private final Map<List<Object>, List<Object[]>> purchases = lru();

  private record MatchFacts(
      Map<Long, PlayerMatchStatisticDomain> players, Map<Long, Integer> roles) {}

  private static <K, V> Map<K, V> lru() {
    return java.util.Collections.synchronizedMap(
        new java.util.LinkedHashMap<>(16, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > CACHED;
          }
        });
  }

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
    MatchFacts facts = matches.computeIfAbsent(purchase.matchId(), this::load);
    Optional<PlayerMatchStatisticDomain> player =
        Optional.ofNullable(facts.players().get(purchase.slot()));
    OptionalLong subjectId =
        kits.item(purchase.itemKey()).map(i -> i.getId()).stream()
            .filter(java.util.Objects::nonNull)
            .mapToLong(Long::longValue)
            .findFirst();
    if (player.isEmpty() || player.get().getHeroId() == null || subjectId.isEmpty()) {
      return Result.NONE;
    }
    Integer role = role(facts, purchase.slot(), player.get());
    if (role == null) {
      return Result.NONE;
    }
    long from = Math.round(purchase.seconds() - settings.timeBand());
    long to = Math.round(purchase.seconds() + settings.timeBand());
    long since =
        clock
            .instant()
            .minus(WINDOW)
            .truncatedTo(java.time.temporal.ChronoUnit.DAYS)
            .getEpochSecond();
    List<Object[]> rows =
        purchases.computeIfAbsent(
            List.of(player.get().getHeroId(), role, from, to, purchase.matchId(), since),
            key ->
                itemRepo.findHeroPurchases(
                    player.get().getHeroId(),
                    role,
                    role == MID_ROLE ? MID_LANE : null,
                    from,
                    to,
                    LEAD_MINUTES,
                    purchase.matchId(),
                    since));
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

  private MatchFacts load(long matchId) {
    Map<Long, PlayerMatchStatisticDomain> players = new HashMap<>();
    statRepo.findAllByMatchId(matchId).stream()
        .filter(p -> p.getPlayerSlot() != null)
        .forEach(p -> players.put(p.getPlayerSlot(), p));
    Map<Long, Integer> roles = new HashMap<>();
    roleRepo.findByMatchId(matchId).stream()
        .filter(r -> r.getPlayerSlot() != null && r.getRole() != null)
        .forEach(r -> roles.putIfAbsent(r.getPlayerSlot(), r.getRole()));
    return new MatchFacts(players, roles);
  }

  /** The stored role; without one only the middle lane tells a role, a side lane holds two. */
  private static Integer role(MatchFacts facts, long slot, PlayerMatchStatisticDomain player) {
    Integer stored = facts.roles().get(slot);
    if (stored != null) {
      return stored;
    }
    return player.getLaneRole() != null && player.getLaneRole() == MID_LANE ? MID_ROLE : null;
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
