package com.ako.dbuff.service.match.mapper;

import com.ako.dbuff.context.ProcessContext;
import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.KillLogDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.repo.AbilityRepo;
import com.ako.dbuff.dao.repo.ItemRepository;
import com.ako.dbuff.dao.repo.KillLogRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.PlayerRepo;
import com.ako.dbuff.dotapi.model.MatchResponsePlayersInner;
import com.ako.dbuff.dotapi.model.MatchResponsePlayersInnerKillsLogInner;
import com.ako.dbuff.dotapi.model.MatchResponsePlayersInnerNeutralItemHistoryInner;
import com.ako.dbuff.dotapi.model.MatchResponsePlayersInnerPurchaseLogInner;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.AbilityIdsConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.constant.data.ItemConstant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class PlayersMapper {

  private final PlayerRepo playerRepo;
  private final PlayerGameStatisticRepo playerGameStatisticRepo;
  private final ItemRepository itemRepository;
  private final AbilityRepo abilityRepo;
  private final KillLogRepo killLogRepo;
  private final ConstantsManagers constantManagers;

  /**
   * Handles mapping of player data from API response to domain objects. Uses ScopedValue to set
   * player context for logging within this scope.
   *
   * @param matchDomain the match domain
   * @param player the player data from API
   * @param heroToPlayerMap map of hero internal name to player info (slot, accountId, prettyName)
   */
  public void handle(
      MatchDomain matchDomain,
      MatchResponsePlayersInner player,
      Map<String, PlayerHeroInfo> heroToPlayerMap) {
    Long accountId = Optional.ofNullable(player.getAccountId()).orElse(-1L);
    String personaname = Optional.ofNullable(player.getPersonaname()).orElse("anonymous");

    // Run player processing with playerId in scope for logging
    ProcessContext.runWithPlayerId(
        accountId, () -> processPlayer(matchDomain, player, accountId, personaname, heroToPlayerMap));
  }

  /**
   * Record to hold player info for kill log mapping. Used to map killed hero name to player
   * details.
   */
  public record PlayerHeroInfo(Long playerSlot, Long accountId, String heroPrettyName) {}

  private void processPlayer(
      MatchDomain matchDomain,
      MatchResponsePlayersInner player,
      Long accountId,
      String personaname,
      Map<String, PlayerHeroInfo> heroToPlayerMap) {
    String ctx = ProcessContext.getContextString();
    log.debug("{} Processing player {} for match {}", ctx, accountId, matchDomain.getId());

    PlayerDomain playerDomain = getOrCreatePlayer(accountId, personaname);
    PlayerMatchStatisticDomain playerStatisticDomain =
        createPlayerStatistics(matchDomain, player, accountId);

    Long heroId = player.getHeroId();
    HeroConstant hero = constantManagers.getHeroConstantMap().get(String.valueOf(heroId));

    setHeroInfo(playerStatisticDomain, heroId, hero);
    setPlayerStats(playerStatisticDomain, player);
    setBenchmarks(player, playerStatisticDomain);

    // Parse damage inflictor data before processing abilities and items
    DamageInflictorData damageData = parseDamageInflictorData(player);

    Set<Pair<AbilityIdsConstant, AbilityConstant>> playerAbilities =
        processAbilities(player, matchDomain, playerDomain, damageData);
    playerStatisticDomain.setHasAbilities(!playerAbilities.isEmpty());
    playerStatisticDomain.setDotaApiAbilities(!playerAbilities.isEmpty());

    processItems(player, matchDomain, playerDomain, playerStatisticDomain, damageData);
    processKillsLog(player, matchDomain, playerDomain, heroToPlayerMap);

    // Set hand damage stats
    playerStatisticDomain.setHandDamageDealt(damageData.handDamageDealt());
    playerStatisticDomain.setHandDamageReceived(damageData.handDamageReceived());

    playerGameStatisticRepo.save(playerStatisticDomain);
    log.debug(
        "{} Saved player statistics for player {} in match {}",
        ctx,
        accountId,
        matchDomain.getId());
  }

  private PlayerDomain getOrCreatePlayer(Long accountId, String personaname) {
    return playerRepo
        .findById(accountId)
        .orElseGet(() -> playerRepo.save(new PlayerDomain(accountId, personaname)));
  }

  private PlayerMatchStatisticDomain createPlayerStatistics(
      MatchDomain matchDomain, MatchResponsePlayersInner player, Long accountId) {
    PlayerMatchStatisticDomain stats = new PlayerMatchStatisticDomain();
    stats.setPlayerId(accountId);
    stats.setMatchId(matchDomain.getId());
    stats.setPlayerSlot(player.getPlayerSlot());
    stats.setObsPlaced(player.getObsPlaced());
    stats.setSenPlaced(player.getSenPlaced());
    stats.setCreepsStacked(player.getCreepsStacked());
    stats.setLastHits(player.getLastHits());
    stats.setDenies(player.getDenies());
    stats.setCampsStacked(player.getCampsStacked());
    stats.setRunePickups(player.getRunePickups());
    stats.setTowerKills(player.getTowerKills());
    stats.setRoshanKills(player.getRoshanKills());
    return stats;
  }

  private void setHeroInfo(PlayerMatchStatisticDomain stats, Long heroId, HeroConstant hero) {
    stats.setHeroId(heroId);
    stats.setHeroName(hero.getName());
    stats.setHeroPrettyName(hero.getLocalized_name());
  }

  private void setPlayerStats(PlayerMatchStatisticDomain stats, MatchResponsePlayersInner player) {
    // Existing fields
    stats.setPartySize(player.getPartySize());
    stats.setAganim(player.getAghanimsScepter());
    stats.setAganimShard(player.getAghanimsShard());
    stats.setMoonshard(player.getMoonshard());
    stats.setWin(player.getWin());
    stats.setTotalGold(player.getTotalGold());
    stats.setTotalXp(player.getTotalXp());
    stats.setKda(player.getKda());
    stats.setAbandons(player.getAbandons());
    stats.setNeutralKills(player.getNeutralKills());
    stats.setCourierKills(player.getCourierKills());
    stats.setLane(player.getLane());
    stats.setLaneEfficiency(player.getLaneEfficiency());
    stats.setLaneEfficiencyPct(player.getLaneEfficiencyPct());
    stats.setHeroDamage(player.getHeroDamage());
    stats.setTowerDamage(player.getTowerDamage());
    stats.setHeroHealing(player.getHeroHealing());
    stats.setComputedMmmr(player.getComputedMmr());
    stats.setDamageTaken(calculateDamageTaken(player.getDamageTaken()));

    // High Priority - Core Stats
    stats.setKills(player.getKills());
    stats.setDeaths(player.getDeaths());
    stats.setAssists(player.getAssists());
    stats.setLevel(player.getLevel());
    stats.setNetWorth(player.getNetWorth());
    stats.setIsRadiant(player.getIsRadiant());

    // Medium Priority - Performance Metrics
    stats.setStuns(player.getStuns());
    stats.setTeamfightParticipation(player.getTeamfightParticipation());
    stats.setActionsPerMin(player.getActionsPerMin());
    stats.setLaneRole(player.getLaneRole());
    stats.setRankTier(player.getRankTier());
    stats.setHeroVariant(player.getHeroVariant());
    stats.setBuybackCount(player.getBuybackCount());
    stats.setGoldSpent(player.getGoldSpent());

    // Timeline Stats (Gold, Last Hits, and XP)
    setTimelineStats(stats, player.getTimes(), player.getGoldT(), player.getLhT(), player.getXpT());
  }

  /**
   * Extracts gold, last hits, and XP values at specific game times (5, 10, 15, 20 minutes). The
   * times array contains timestamps in seconds (0, 60, 120, ...) and gold_t/lh_t/xp_t contain
   * corresponding values. If the game ended before a specific time, the value will be null.
   *
   * @param stats the player statistics domain to populate
   * @param times list of timestamps in seconds
   * @param goldT list of gold values corresponding to each timestamp
   * @param lhT list of last hits values corresponding to each timestamp
   * @param xpT list of XP values corresponding to each timestamp
   */
  private void setTimelineStats(
      PlayerMatchStatisticDomain stats,
      List<Long> times,
      List<Long> goldT,
      List<Long> lhT,
      List<Long> xpT) {
    if (times == null || times.isEmpty()) {
      return;
    }

    // Gold Timeline (5, 10, 15, 20 minutes)
    if (goldT != null && !goldT.isEmpty()) {
      stats.setGoldAt5Min(getValueAtTime(times, goldT, 300L));
      stats.setGoldAt10Min(getValueAtTime(times, goldT, 600L));
      stats.setGoldAt15Min(getValueAtTime(times, goldT, 900L));
      stats.setGoldAt20Min(getValueAtTime(times, goldT, 1200L));
    }

    // Last Hits Timeline (5, 10, 15, 20 minutes)
    if (lhT != null && !lhT.isEmpty()) {
      stats.setLastHitsAt5Min(getValueAtTime(times, lhT, 300L));
      stats.setLastHitsAt10Min(getValueAtTime(times, lhT, 600L));
      stats.setLastHitsAt15Min(getValueAtTime(times, lhT, 900L));
      stats.setLastHitsAt20Min(getValueAtTime(times, lhT, 1200L));
    }

    // XP Timeline (5, 10, 15, 20 minutes)
    if (xpT != null && !xpT.isEmpty()) {
      stats.setXpAt5Min(getValueAtTime(times, xpT, 300L));
      stats.setXpAt10Min(getValueAtTime(times, xpT, 600L));
      stats.setXpAt15Min(getValueAtTime(times, xpT, 900L));
      stats.setXpAt20Min(getValueAtTime(times, xpT, 1200L));
    }
  }

  /**
   * Gets a value at a specific time from a timeline array. Finds the index in the times array where
   * the time matches the target time, then returns the corresponding value.
   *
   * @param times list of timestamps in seconds
   * @param values list of values (gold, last hits, etc.)
   * @param targetTimeSeconds the target time in seconds
   * @return the value at that time, or null if the game ended before that time
   */
  private Long getValueAtTime(List<Long> times, List<Long> values, Long targetTimeSeconds) {
    // Find the index where time matches the target
    for (int i = 0; i < times.size(); i++) {
      if (times.get(i).equals(targetTimeSeconds)) {
        // Ensure we have a corresponding value
        if (i < values.size()) {
          return values.get(i);
        }
        return null;
      }
    }
    // Target time not found - game ended before this time
    return null;
  }

  private Long calculateDamageTaken(Object damageTaken) {
    if (damageTaken == null) {
      return null;
    }
    @SuppressWarnings("unchecked")
    Map<String, Number> damageTakenMap = (Map<String, Number>) damageTaken;

    return damageTakenMap.entrySet().stream()
        .filter(e -> e.getKey().startsWith("npc_dota_hero"))
        .mapToLong(e -> e.getValue().longValue())
        .sum();
  }

  private void setBenchmarks(MatchResponsePlayersInner player, PlayerMatchStatisticDomain stats) {
    @SuppressWarnings("unchecked")
    Map<String, Map<String, Integer>> benchmarks =
        (Map<String, Map<String, Integer>>) player.getBenchmarks();
    if (benchmarks != null) {
      setUpBenchmarks(benchmarks, stats);
    }
  }

  private Set<Pair<AbilityIdsConstant, AbilityConstant>> processAbilities(
      MatchResponsePlayersInner player,
      MatchDomain matchDomain,
      PlayerDomain playerDomain,
      DamageInflictorData damageData) {

    Set<String> allHeroAbilities = constantManagers.getAllHeroAbilities();
    Map<String, AbilityConstant> allAbilities = constantManagers.getAllAbilityConstants();
    Map<String, AbilityIdsConstant> abilityIdsConstantMap =
        constantManagers.getAbilityConstantMap();
    Long playerSlot = Long.valueOf(player.getPlayerSlot());

    Set<Pair<AbilityIdsConstant, AbilityConstant>> playerAbilities =
        new HashSet<>(CollectionUtils.emptyIfNull(player.getAbilityUpgradesArr()))
            .stream()
                .map(abilityId -> abilityIdsConstantMap.get(String.valueOf(abilityId)))
                .filter(
                    ablIdConst ->
                        ablIdConst != null && allHeroAbilities.contains(ablIdConst.getName()))
                .map(ablIdConst -> Pair.of(ablIdConst, allAbilities.get(ablIdConst.getName())))
                .collect(Collectors.toSet());

    playerAbilities.forEach(
        ability -> addAbility(ability, matchDomain, playerDomain, playerSlot, damageData));
    return playerAbilities;
  }

  private void processItems(
      MatchResponsePlayersInner player,
      MatchDomain matchDomain,
      PlayerDomain playerDomain,
      PlayerMatchStatisticDomain stats,
      DamageInflictorData damageData) {
    Map<String, ItemConstant> itemsConstant = constantManagers.getItemConstantMap();
    Long playerSlot = Long.valueOf(player.getPlayerSlot());

    Collection<MatchResponsePlayersInnerPurchaseLogInner> purchaseLogs =
        CollectionUtils.emptyIfNull(player.getPurchaseLog());
    stats.setDotaApiItems(!purchaseLogs.isEmpty());

    purchaseLogs.forEach(
        item ->
            addItemPurchase(item, itemsConstant, matchDomain, playerDomain, playerSlot, damageData));
    stats.setHasItems(!purchaseLogs.isEmpty());

    Collection<MatchResponsePlayersInnerNeutralItemHistoryInner> neutralItemHistory =
        CollectionUtils.emptyIfNull(player.getNeutralItemHistory());
    neutralItemHistory.forEach(
        item ->
            addNeutralItem(item, itemsConstant, matchDomain, playerDomain, playerSlot, damageData));
    stats.setHasNeutralItems(!neutralItemHistory.isEmpty());
  }

  /**
   * Processes the kills_log for a player and saves each kill event to the database. Maps the killed
   * hero name to the killed player's slot, account ID, and pretty hero name using the provided
   * heroToPlayerMap.
   *
   * @param player the player data from API
   * @param matchDomain the match domain
   * @param playerDomain the killer's player domain
   * @param heroToPlayerMap map of hero internal name to player info
   */
  private void processKillsLog(
      MatchResponsePlayersInner player,
      MatchDomain matchDomain,
      PlayerDomain playerDomain,
      Map<String, PlayerHeroInfo> heroToPlayerMap) {
    Long playerSlot = Long.valueOf(player.getPlayerSlot());

    Collection<MatchResponsePlayersInnerKillsLogInner> killsLog =
        CollectionUtils.emptyIfNull(player.getKillsLog());

    killsLog.forEach(
        kill -> saveKillLog(kill, matchDomain, playerDomain, playerSlot, heroToPlayerMap));

    if (!killsLog.isEmpty()) {
      log.debug(
          "{} Saved {} kill log entries for player {} in match {}",
          ProcessContext.getContextString(),
          killsLog.size(),
          playerDomain.getId(),
          matchDomain.getId());
    }
  }

  /**
   * Saves a single kill log entry to the database.
   *
   * @param kill the kill log entry from API
   * @param matchDomain the match domain
   * @param playerDomain the killer's player domain
   * @param playerSlot the killer's player slot
   * @param heroToPlayerMap map of hero internal name to player info
   * @return the saved KillLogDomain, or null if the killed hero couldn't be mapped
   */
  private KillLogDomain saveKillLog(
      MatchResponsePlayersInnerKillsLogInner kill,
      MatchDomain matchDomain,
      PlayerDomain playerDomain,
      Long playerSlot,
      Map<String, PlayerHeroInfo> heroToPlayerMap) {
    String killedHeroName = kill.getKey();
    Long time = kill.getTime();

    if (killedHeroName == null) {
      log.debug("{} Kill log entry has null hero name", ProcessContext.getContextString());
      return null;
    }

    KillLogDomain killLog = new KillLogDomain();
    killLog.setMatchId(matchDomain.getId());
    killLog.setPlayerSlot(playerSlot);
    killLog.setPlayerId(playerDomain.getId());
    killLog.setTime(time);
    killLog.setKilledHeroName(killedHeroName);

    // Look up the killed player's info from the map
    PlayerHeroInfo killedPlayerInfo = heroToPlayerMap.get(killedHeroName);
    if (killedPlayerInfo != null) {
      killLog.setKilledPlayerSlot(killedPlayerInfo.playerSlot());
      killLog.setKilledPlayerId(killedPlayerInfo.accountId());
      killLog.setKilledHeroPrettyName(killedPlayerInfo.heroPrettyName());
    } else {
      log.warn(
          "{} Could not find player info for killed hero: {}",
          ProcessContext.getContextString(),
          killedHeroName);
    }

    return killLogRepo.save(killLog);
  }

  /**
   * Adds an ability to the database. Uses playerSlot as part of the primary key since anonymous
   * players have playerId = -1.
   */
  private AbilityDomain addAbility(
      Pair<AbilityIdsConstant, AbilityConstant> abilityPair,
      MatchDomain matchDomain,
      PlayerDomain playerDomain,
      Long playerSlot,
      DamageInflictorData damageData) {

    AbilityDomain abilityDomain = new AbilityDomain();
    abilityDomain.setPlayerId(playerDomain.getId());
    abilityDomain.setPlayerSlot(playerSlot);
    abilityDomain.setMatchId(matchDomain.getId());

    AbilityIdsConstant id = abilityPair.getFirst();
    AbilityConstant name = abilityPair.getSecond();

    abilityDomain.setAbilityId(id.getId());
    abilityDomain.setName(id.getName());
    abilityDomain.setPrettyName(name.getDname());

    // Set damage dealt and received from damage inflictor data
    String abilityName = id.getName();
    abilityDomain.setDamageDealt(damageData.abilityDamageDealt().get(abilityName));
    abilityDomain.setDamageReceived(damageData.abilityDamageReceived().get(abilityName));

    // Set use count from ability_uses data
    abilityDomain.setUseCount(damageData.abilityUseCounts().get(abilityName));

    return abilityRepo.save(abilityDomain);
  }

  private void setUpBenchmarks(
      Map<String, Map<String, Integer>> benchmarks,
      PlayerMatchStatisticDomain playerMatchStatisticDomain) {
    Long xp = Long.valueOf(benchmarks.get("xp_per_min").get("raw"));
    Long gold = Long.valueOf(benchmarks.get("gold_per_min").get("raw"));
    playerMatchStatisticDomain.setXpPerMin(xp);
    playerMatchStatisticDomain.setGoldPerMin(gold);
  }

  private ItemDomain addNeutralItem(
      MatchResponsePlayersInnerNeutralItemHistoryInner item,
      Map<String, ItemConstant> itemsConstant,
      MatchDomain matchDomain,
      PlayerDomain playerDomain,
      Long playerSlot,
      DamageInflictorData damageData) {
    String itemName = item.getItemNeutral();

    if (itemName == null) {
      log.debug("{} itemName is null", ProcessContext.getContextString());
      return null;
    }

    Long time = item.getTime();
    String itemEnhancement = item.getItemNeutralEnhancement();
    return saveItemDomain(
        itemsConstant,
        matchDomain,
        playerDomain,
        playerSlot,
        itemName,
        time,
        itemEnhancement,
        damageData);
  }

  private ItemDomain addItemPurchase(
      MatchResponsePlayersInnerPurchaseLogInner item,
      Map<String, ItemConstant> itemsConstant,
      MatchDomain matchDomain,
      PlayerDomain playerDomain,
      Long playerSlot,
      DamageInflictorData damageData) {
    String itemName = item.getKey();
    Long time = item.getTime();

    return saveItemDomain(
        itemsConstant, matchDomain, playerDomain, playerSlot, itemName, time, null, damageData);
  }

  /**
   * Saves an item to the database. Uses playerSlot as part of the primary key since anonymous
   * players have playerId = -1.
   */
  private ItemDomain saveItemDomain(
      Map<String, ItemConstant> itemsConstant,
      MatchDomain matchDomain,
      PlayerDomain playerDomain,
      Long playerSlot,
      String itemName,
      Long time,
      String neutralEnhancement,
      DamageInflictorData damageData) {
    ItemConstant itemConstant = itemsConstant.get(itemName);

    if (itemConstant == null) {
      log.warn("{} Item constant not found for: {}", ProcessContext.getContextString(), itemName);
      return null;
    }

    String dname = itemConstant.getDname();
    Long id = itemConstant.getId();

    ItemDomain itemDomain = new ItemDomain();
    itemDomain.setItemId(id);
    itemDomain.setMatchId(matchDomain.getId());
    itemDomain.setPlayerId(playerDomain.getId());
    itemDomain.setPlayerSlot(playerSlot);
    itemDomain.setItemName(itemName);
    itemDomain.setItemPrettyName(dname);
    itemDomain.setItemPurchaseTime(time);

    itemDomain.setNeutral(neutralEnhancement != null);
    itemDomain.setNeutralEnhancement(neutralEnhancement);

    // Set damage dealt and received from damage inflictor data
    itemDomain.setDamageDealt(damageData.itemDamageDealt().get(itemName));
    itemDomain.setDamageReceived(damageData.itemDamageReceived().get(itemName));

    // Set use count from item_uses data
    itemDomain.setUseCount(damageData.itemUseCounts().get(itemName));

    return itemRepository.save(itemDomain);
  }

  // ==================== Damage Inflictor Parsing ====================

  /**
   * Record to hold parsed damage inflictor data. Contains maps for ability damage, item damage,
   * ability use counts, item use counts, and hand attack damage (both dealt and received).
   */
  private record DamageInflictorData(
      Long handDamageDealt,
      Long handDamageReceived,
      Map<String, Long> abilityDamageDealt,
      Map<String, Long> abilityDamageReceived,
      Map<String, Long> itemDamageDealt,
      Map<String, Long> itemDamageReceived,
      Map<String, Long> abilityUseCounts,
      Map<String, Long> itemUseCounts) {}

  /**
   * Parses damage_inflictor, damage_inflictor_received, and ability_uses from player data.
   * Categorizes each damage source as:
   * - "null" key -> hand attack damage
   * - ability name (from AbilityDomain.name) -> ability damage
   * - item name (from ItemDomain.itemName) -> item damage
   *
   * Also parses ability_uses to get the number of times each ability was used.
   *
   * @param player the player data from API
   * @return DamageInflictorData containing categorized damage values and ability use counts
   */
  private DamageInflictorData parseDamageInflictorData(MatchResponsePlayersInner player) {
    Map<String, Long> abilityDamageDealt = new HashMap<>();
    Map<String, Long> abilityDamageReceived = new HashMap<>();
    Map<String, Long> itemDamageDealt = new HashMap<>();
    Map<String, Long> itemDamageReceived = new HashMap<>();
    Map<String, Long> abilityUseCounts = new HashMap<>();
    Map<String, Long> itemUseCounts = new HashMap<>();
    Long handDamageDealt = null;
    Long handDamageReceived = null;

    Set<String> allAbilityNames = constantManagers.getAllHeroAbilities();
    Map<String, ItemConstant> itemConstantMap = constantManagers.getItemConstantMap();

    // Parse damage_inflictor (damage dealt)
    Object damageInflictorObj = player.getDamageInflictor();
    if (damageInflictorObj != null) {
      @SuppressWarnings("unchecked")
      Map<String, Number> damageInflictor = (Map<String, Number>) damageInflictorObj;

      for (Map.Entry<String, Number> entry : damageInflictor.entrySet()) {
        String key = entry.getKey();
        Long damage = entry.getValue().longValue();

        if ("null".equals(key)) {
          // Hand attack damage
          handDamageDealt = damage;
        } else if (allAbilityNames.contains(key)) {
          // Ability damage
          abilityDamageDealt.put(key, damage);
        } else if (itemConstantMap.containsKey(key)) {
          // Item damage
          itemDamageDealt.put(key, damage);
        } else {
          // Unknown source - could be an ability or item not in our constants
          // Try to categorize based on naming patterns
          if (key.contains("_")) {
            // Most abilities have underscores in their names
            abilityDamageDealt.put(key, damage);
          } else {
            // Items typically don't have underscores
            itemDamageDealt.put(key, damage);
          }
        }
      }
    }

    // Parse damage_inflictor_received (damage received)
    Object damageInflictorReceivedObj = player.getDamageInflictorReceived();
    if (damageInflictorReceivedObj != null) {
      @SuppressWarnings("unchecked")
      Map<String, Number> damageInflictorReceived = (Map<String, Number>) damageInflictorReceivedObj;

      for (Map.Entry<String, Number> entry : damageInflictorReceived.entrySet()) {
        String key = entry.getKey();
        Long damage = entry.getValue().longValue();

        if ("null".equals(key)) {
          // Hand attack damage received
          handDamageReceived = damage;
        } else if (allAbilityNames.contains(key)) {
          // Ability damage received
          abilityDamageReceived.put(key, damage);
        } else if (itemConstantMap.containsKey(key)) {
          // Item damage received
          itemDamageReceived.put(key, damage);
        } else {
          // Unknown source - categorize based on naming patterns
          if (key.contains("_")) {
            abilityDamageReceived.put(key, damage);
          } else {
            itemDamageReceived.put(key, damage);
          }
        }
      }
    }

    // Parse ability_uses (number of times each ability was used)
    Object abilityUsesObj = player.getAbilityUses();
    if (abilityUsesObj != null) {
      @SuppressWarnings("unchecked")
      Map<String, Number> abilityUses = (Map<String, Number>) abilityUsesObj;

      for (Map.Entry<String, Number> entry : abilityUses.entrySet()) {
        String abilityName = entry.getKey();
        Long useCount = entry.getValue().longValue();
        abilityUseCounts.put(abilityName, useCount);
      }
    }

    // Parse item_uses (number of times each item was used/activated)
    Object itemUsesObj = player.getItemUses();
    if (itemUsesObj != null) {
      @SuppressWarnings("unchecked")
      Map<String, Number> itemUses = (Map<String, Number>) itemUsesObj;

      for (Map.Entry<String, Number> entry : itemUses.entrySet()) {
        String itemName = entry.getKey();
        Long useCount = entry.getValue().longValue();
        itemUseCounts.put(itemName, useCount);
      }
    }

    return new DamageInflictorData(
        handDamageDealt,
        handDamageReceived,
        abilityDamageDealt,
        abilityDamageReceived,
        itemDamageDealt,
        itemDamageReceived,
        abilityUseCounts,
        itemUseCounts);
  }
}
