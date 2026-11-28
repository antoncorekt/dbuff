package com.ako.dbuff.service.replay.analysis.defense;

import com.ako.dbuff.dao.model.PlayerDefenseTrackDomain;
import com.ako.dbuff.dao.model.ReplayPlayerDomain;
import com.ako.dbuff.dao.model.ReplayPlayerTrackDomain;
import com.ako.dbuff.dao.model.TeamDefensePointDomain;
import com.ako.dbuff.dao.repo.PlayerDefenseTrackRepo;
import com.ako.dbuff.dao.repo.PlayerTimelineRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerRepo;
import com.ako.dbuff.dao.repo.ReplayPlayerTrackRepo;
import com.ako.dbuff.dao.repo.TeamDefensePointRepo;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.replay.CombatEvent;
import com.ako.dbuff.service.replay.CombatEventKind;
import com.ako.dbuff.service.replay.CombatHeroes;
import com.ako.dbuff.service.replay.CombatLogReader;
import com.ako.dbuff.service.replay.DamageType;
import com.ako.dbuff.service.replay.PlayerSlots;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.defense.DefenseItemBonuses.Bonus;
import com.ako.dbuff.service.replay.analysis.role.TeamSide;
import com.ako.dbuff.service.replay.analysis.state.HeroStateTimeline;
import com.ako.dbuff.service.replay.analysis.state.HeroStateTimelines;
import com.ako.dbuff.service.replay.analysis.team.TeamNetWorth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Estimates every hero's armor and magic resistance each game minute from the stored base values,
 * agility and held items, and turns them with max health into physical, magical and mixed effective
 * health per hero and per team. The mix uses the damage types the enemy really dealt.
 */
@Slf4j
@Order(41)
@Component
@RequiredArgsConstructor
public class TeamDefenseAnalyzer implements ReplayAnalyzer {
  private static final int SECONDS_PER_MINUTE = 60;
  private static final int[] CHECKPOINT_MINUTES = {10, 20, 30};
  private static final String END = "END";

  private final ReplayPlayerTrackRepo trackRepo;
  private final ReplayPlayerRepo playerRepo;
  private final PlayerTimelineRepo timelineRepo;
  private final CombatLogReader combatLog;
  private final HeroStateTimelines stateTimelines;
  private final DefenseItemBonuses bonuses;
  private final DefenseProperties properties;
  private final ConstantsManagers constants;
  private final PlayerDefenseTrackRepo defenseTrackRepo;
  private final TeamDefensePointRepo pointRepo;

  private record Sample(
      Integer maxHp,
      Integer hp,
      boolean alive,
      Double armor,
      Double magicResist,
      Double ehpPhys,
      Double ehpMag,
      Double ehpMix) {}

  private record Hero(long slot, TeamSide side, ReplayPlayerTrackDomain track) {}

  @Override
  public String name() {
    return "team-defense";
  }

  @Override
  public int version() {
    return 2;
  }

  @Override
  public Set<String> requires() {
    return Set.of("players", "tracks", "combat-log", "state-changes");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    deleteByMatchId(matchId);
    List<Hero> heroes = heroes(trackRepo.findByMatchId(matchId));
    int lastMinute = lastMinute(heroes);
    if (lastMinute < 0) {
      log.info("Match {} has no player tracks, so no team defense", matchId);
      return;
    }
    double lastSecond = lastSecond(heroes);
    List<CombatEvent> events = combatLog.events(matchId);
    Map<String, TeamSide> teamByHero = teamByHero(events, playerRepo.findByMatchId(matchId));
    List<CombatEvent> damage = heroDamage(events, teamByHero);
    Map<Integer, HeroStateTimeline> timelines = stateTimelines.load(matchId);
    TeamNetWorth netWorth = TeamNetWorth.of(timelineRepo.findByMatchId(matchId));
    Function<String, DamageType> abilityType = abilityTypes();

    List<Double> seconds = new ArrayList<>();
    for (int minute = 0; minute <= lastMinute; minute++) {
      seconds.add(minute * (double) SECONDS_PER_MINUTE);
    }
    seconds.add(lastSecond);

    Map<Long, List<Sample>> samples = new HashMap<>();
    List<Map<TeamSide, DamageShares>> shares = new ArrayList<>();
    for (double second : seconds) {
      Map<TeamSide, DamageShares> byTeam = new EnumMap<>(TeamSide.class);
      for (TeamSide side : TeamSide.values()) {
        byTeam.put(
            side,
            DamageShares.of(
                damage,
                teamByHero,
                side,
                second,
                properties.getWindowMinutes() * (double) SECONDS_PER_MINUTE,
                abilityType));
      }
      shares.add(byTeam);
    }
    for (Hero hero : heroes) {
      HeroStateTimeline timeline = timelines.get(PlayerSlots.index(hero.slot()));
      List<Sample> list = new ArrayList<>();
      for (int i = 0; i < seconds.size(); i++) {
        list.add(sample(hero, timeline, seconds.get(i), shares.get(i).get(hero.side())));
      }
      samples.put(hero.slot(), list);
    }

    pointRepo.saveAll(points(matchId, heroes, samples, shares, netWorth, lastMinute));
    defenseTrackRepo.saveAll(tracks(matchId, heroes, samples, lastMinute));
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    pointRepo.deleteByMatchId(matchId);
    defenseTrackRepo.deleteByMatchId(matchId);
  }

  private static List<Hero> heroes(List<ReplayPlayerTrackDomain> tracks) {
    Map<Long, Hero> bySlot = new HashMap<>();
    for (ReplayPlayerTrackDomain t : tracks) {
      if (t.getPlayerSlot() != null
          && t.getStartSeconds() != null
          && t.getStepSeconds() != null
          && t.getStepSeconds() > 0
          && t.getSampleCount() != null
          && t.getSampleCount() > 0) {
        bySlot.putIfAbsent(
            t.getPlayerSlot(), new Hero(t.getPlayerSlot(), TeamSide.ofSlot(t.getPlayerSlot()), t));
      }
    }
    return bySlot.values().stream().sorted(java.util.Comparator.comparingLong(Hero::slot)).toList();
  }

  private static double lastSecond(List<Hero> heroes) {
    double last = Double.NEGATIVE_INFINITY;
    for (Hero h : heroes) {
      ReplayPlayerTrackDomain t = h.track();
      last = Math.max(last, t.getStartSeconds() + (t.getSampleCount() - 1.0) * t.getStepSeconds());
    }
    return last;
  }

  private static int lastMinute(List<Hero> heroes) {
    return heroes.isEmpty()
        ? -1
        : (int) Math.max(0, Math.floor(lastSecond(heroes) / SECONDS_PER_MINUTE));
  }

  private static Map<String, TeamSide> teamByHero(
      List<CombatEvent> events, List<ReplayPlayerDomain> roster) {
    Set<String> names = CombatHeroes.names(events);
    Map<String, TeamSide> sides = new HashMap<>();
    for (ReplayPlayerDomain player : roster) {
      String name = CombatHeroes.nameOf(player, names);
      if (name != null && player.getPlayerSlot() != null) {
        sides.put(name, TeamSide.ofSlot(player.getPlayerSlot()));
      }
    }
    return sides;
  }

  private static List<CombatEvent> heroDamage(
      List<CombatEvent> events, Map<String, TeamSide> teamByHero) {
    return events.stream()
        .filter(
            e ->
                e.kind() == CombatEventKind.DAMAGE
                    && e.attackerIsRealHero()
                    && e.targetIsRealHero()
                    && teamByHero.containsKey(e.attacker())
                    && teamByHero.containsKey(e.target()))
        .toList();
  }

  private Function<String, DamageType> abilityTypes() {
    Map<String, AbilityConstant> abilities = constants.getAllAbilityConstants();
    return name -> {
      AbilityConstant ability = abilities == null ? null : abilities.get(name);
      if (ability == null || ability.getDmg_type() == null || ability.getDmg_type().isEmpty()) {
        return DamageType.UNKNOWN;
      }
      return switch (ability.getDmg_type().get(0).toLowerCase(Locale.ROOT)) {
        case "physical" -> DamageType.PHYSICAL;
        case "magical" -> DamageType.MAGICAL;
        case "pure" -> DamageType.PURE;
        default -> DamageType.UNKNOWN;
      };
    };
  }

  private Sample sample(Hero hero, HeroStateTimeline timeline, double second, DamageShares shares) {
    ReplayPlayerTrackDomain t = hero.track();
    int index =
        (int)
            Math.max(
                0,
                Math.min(
                    t.getSampleCount() - 1,
                    Math.round((second - t.getStartSeconds()) / t.getStepSeconds())));
    double sampleSecond = t.getStartSeconds() + (double) index * t.getStepSeconds();
    Integer maxHp = at(t.getMaxHp(), index);
    Integer hpPercent = at(t.getHpPercent(), index);
    Integer flags = at(t.getFlags(), index);
    Integer hp = maxHp == null || hpPercent == null ? null : maxHp * hpPercent / 100;
    boolean alive = flags != null && (flags & 1) != 0;
    int source = index;
    if (maxHp == null || maxHp <= 0) {
      source = lastValidIndex(t, index);
    }
    Integer baseArmor = at(t.getBaseArmorX10(), source);
    Integer baseMr = at(t.getBaseMagicResistX10(), source);
    Integer agility = at(t.getAgilityTotalX10(), source);
    maxHp = at(t.getMaxHp(), source);
    if (baseArmor == null || baseMr == null || agility == null) {
      return new Sample(maxHp, hp, alive, null, null, null, null, null);
    }
    List<Bonus> held =
        timeline == null
            ? List.of()
            : timeline.itemsAt(sampleSecond).stream().map(bonuses::of).toList();
    double armor =
        DefenseMath.armor(baseArmor / 10.0, agility / 10.0, properties.getArmorPerAgility(), held);
    double mr = DefenseMath.magicResist(baseMr / 1000.0, held);
    if (maxHp == null || maxHp <= 0) {
      return new Sample(maxHp, hp, alive, armor, mr, null, null, null);
    }
    Double mixed =
        shares == null
            ? null
            : DefenseMath.ehpMixed(
                maxHp, armor, mr, shares.physical(), shares.magical(), shares.pure());
    return new Sample(
        maxHp,
        hp,
        alive,
        armor,
        mr,
        DefenseMath.ehpPhysical(maxHp, armor),
        DefenseMath.ehpMagical(maxHp, mr),
        mixed);
  }

  private static int lastValidIndex(ReplayPlayerTrackDomain t, int index) {
    for (int i = index - 1; i >= 0; i--) {
      Integer earlier = at(t.getMaxHp(), i);
      if (earlier != null && earlier > 0) {
        return i;
      }
    }
    return index;
  }

  private static Integer at(int[] values, int index) {
    return values == null || index >= values.length ? null : values[index];
  }

  private List<TeamDefensePointDomain> points(
      long matchId,
      List<Hero> heroes,
      Map<Long, List<Sample>> samples,
      List<Map<TeamSide, DamageShares>> shares,
      TeamNetWorth netWorth,
      int lastMinute) {
    List<TeamDefensePointDomain> rows = new ArrayList<>();
    for (TeamSide side : TeamSide.values()) {
      List<Long> slots = heroes.stream().filter(h -> h.side() == side).map(Hero::slot).toList();
      if (slots.isEmpty()) {
        continue;
      }
      for (int i = 0; i <= lastMinute + 1; i++) {
        int row = i;
        boolean end = i == lastMinute + 1;
        int minute = end ? lastMinute : i;
        rows.add(
            point(
                matchId,
                side,
                minute,
                end ? END : checkpoint(minute),
                slots.stream().map(s -> samples.get(s).get(row)).toList(),
                shares.get(i).get(side),
                netWorth == null ? null : netWorthAt(netWorth.of(side), minute)));
      }
    }
    return rows;
  }

  private TeamDefensePointDomain point(
      long matchId,
      TeamSide side,
      int minute,
      String checkpoint,
      List<Sample> team,
      DamageShares shares,
      Integer netWorth) {
    return TeamDefensePointDomain.builder()
        .matchId(matchId)
        .team(side.name())
        .minute(minute)
        .maxHpSum(sum(team, Sample::maxHp))
        .hpSum(sum(team, Sample::hp))
        .armorAvgX10(average(team, Sample::armor, 10))
        .magicResistAvgX10(average(team, Sample::magicResist, 1000))
        .ehpPhys(sumRounded(team, Sample::ehpPhys))
        .ehpMag(sumRounded(team, Sample::ehpMag))
        .ehpMix(sumRounded(team, Sample::ehpMix))
        .enemyPhysShare(shares == null ? null : shares.physical())
        .enemyMagShare(shares == null ? null : shares.magical())
        .enemyPureShare(shares == null ? null : shares.pure())
        .alive((int) team.stream().filter(Sample::alive).count())
        .netWorth(netWorth)
        .checkpoint(checkpoint)
        .analysisVersion(version())
        .build();
  }

  private static Integer netWorthAt(double[] byMinute, int minute) {
    return minute < byMinute.length ? (int) Math.round(byMinute[minute]) : null;
  }

  private static String checkpoint(int minute) {
    for (int m : CHECKPOINT_MINUTES) {
      if (m == minute) {
        return "M" + m;
      }
    }
    return null;
  }

  private static Integer sum(List<Sample> team, Function<Sample, Integer> value) {
    int total = 0;
    for (Sample s : team) {
      Integer v = value.apply(s);
      if (v == null) {
        return null;
      }
      total += v;
    }
    return total;
  }

  private static Integer sumRounded(List<Sample> team, Function<Sample, Double> value) {
    double total = 0;
    for (Sample s : team) {
      Double v = value.apply(s);
      if (v == null) {
        return null;
      }
      total += v;
    }
    return (int) Math.round(total);
  }

  private static Integer average(List<Sample> team, Function<Sample, Double> value, int scale) {
    Integer total = sumRounded(team, value.andThen(v -> v == null ? null : v * scale));
    return total == null ? null : (int) Math.round(total / (double) team.size());
  }

  private List<PlayerDefenseTrackDomain> tracks(
      long matchId, List<Hero> heroes, Map<Long, List<Sample>> samples, int lastMinute) {
    List<PlayerDefenseTrackDomain> rows = new ArrayList<>();
    for (Hero hero : heroes) {
      List<Sample> minutes = samples.get(hero.slot()).subList(0, lastMinute + 1);
      rows.add(
          PlayerDefenseTrackDomain.builder()
              .matchId(matchId)
              .playerSlot(hero.slot())
              .startMinute(0)
              .maxHp(series(minutes, Sample::maxHp))
              .hp(series(minutes, Sample::hp))
              .armorX10(series(minutes, s -> scaled(s.armor(), 10)))
              .magicResistX10(series(minutes, s -> scaled(s.magicResist(), 1000)))
              .ehpPhys(series(minutes, s -> scaled(s.ehpPhys(), 1)))
              .ehpMag(series(minutes, s -> scaled(s.ehpMag(), 1)))
              .ehpMix(series(minutes, s -> scaled(s.ehpMix(), 1)))
              .analysisVersion(version())
              .build());
    }
    return rows;
  }

  private static Integer scaled(Double value, int scale) {
    return value == null ? null : (int) Math.round(value * scale);
  }

  private static int[] series(List<Sample> minutes, Function<Sample, Integer> value) {
    int[] out = new int[minutes.size()];
    boolean any = false;
    for (int i = 0; i < out.length; i++) {
      Integer v = value.apply(minutes.get(i));
      out[i] = v == null ? PlayerDefenseTrackDomain.MISSING : v;
      any |= v != null;
    }
    return any ? out : null;
  }
}
