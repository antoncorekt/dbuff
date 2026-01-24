package com.ako.dbuff.service.match.mapper;

import com.ako.dbuff.context.ProcessContext;
import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.repo.AbilityRepo;
import com.ako.dbuff.dao.repo.ItemRepository;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.dao.repo.PlayerRepo;
import com.ako.dbuff.dotapi.model.MatchResponsePlayersInner;
import com.ako.dbuff.dotapi.model.MatchResponsePlayersInnerNeutralItemHistoryInner;
import com.ako.dbuff.dotapi.model.MatchResponsePlayersInnerPurchaseLogInner;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.AbilityIdsConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.constant.data.ItemConstant;
import java.util.Collection;
import java.util.HashSet;
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
  private final ConstantsManagers constantManagers;

  /**
   * Handles mapping of player data from API response to domain objects.
   * Uses ScopedValue to set player context for logging within this scope.
   *
   * @param matchDomain the match domain
   * @param player the player data from API
   */
  public void handle(MatchDomain matchDomain, MatchResponsePlayersInner player) {
    Long accountId = Optional.ofNullable(player.getAccountId()).orElse(-1L);
    String personaname = Optional.ofNullable(player.getPersonaname()).orElse("anonymous");

    // Run player processing with playerId in scope for logging
    ProcessContext.runWithPlayerId(accountId, () -> 
        processPlayer(matchDomain, player, accountId, personaname)
    );
  }

  private void processPlayer(MatchDomain matchDomain, MatchResponsePlayersInner player, 
                             Long accountId, String personaname) {
    String ctx = ProcessContext.getContextString();
    log.debug("{} Processing player {} for match {}", ctx, accountId, matchDomain.getId());

    PlayerDomain playerDomain = getOrCreatePlayer(accountId, personaname);
    PlayerMatchStatisticDomain playerStatisticDomain = createPlayerStatistics(matchDomain, player, accountId);

    Long heroId = player.getHeroId();
    HeroConstant hero = constantManagers.getHeroConstantMap().get(String.valueOf(heroId));

    setHeroInfo(playerStatisticDomain, heroId, hero);
    setPlayerStats(playerStatisticDomain, player);
    setBenchmarks(player, playerStatisticDomain);

    Set<Pair<AbilityIdsConstant, AbilityConstant>> playerAbilities = processAbilities(player, matchDomain, playerDomain);
    playerStatisticDomain.setHasAbilities(!playerAbilities.isEmpty());

    processItems(player, matchDomain, playerDomain, playerStatisticDomain);

    playerGameStatisticRepo.save(playerStatisticDomain);
    log.debug("{} Saved player statistics for player {} in match {}", ctx, accountId, matchDomain.getId());
  }

  private PlayerDomain getOrCreatePlayer(Long accountId, String personaname) {
    return playerRepo
        .findById(accountId)
        .orElseGet(() -> playerRepo.save(new PlayerDomain(accountId, personaname)));
  }

  private PlayerMatchStatisticDomain createPlayerStatistics(MatchDomain matchDomain, 
                                                            MatchResponsePlayersInner player, 
                                                            Long accountId) {
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
//    stats.setAganim(player.getAghanimsScepter()); // todo add
//    stats.setAganimShard(player.getAghanimsShard());
//    stats.setMoonshard(player.getMoonshard());
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
      MatchResponsePlayersInner player, MatchDomain matchDomain, PlayerDomain playerDomain) {
    
    Set<String> allHeroAbilities = constantManagers.getAllHeroAbilities();
    Map<String, AbilityConstant> allAbilities = constantManagers.getAllAbilityConstants();
    Map<String, AbilityIdsConstant> abilityIdsConstantMap = constantManagers.getAbilityConstantMap();
    Long playerSlot = Long.valueOf(player.getPlayerSlot());

    Set<Pair<AbilityIdsConstant, AbilityConstant>> playerAbilities =
        new HashSet<>(CollectionUtils.emptyIfNull(player.getAbilityUpgradesArr()))
            .stream()
            .map(abilityId -> abilityIdsConstantMap.get(String.valueOf(abilityId)))
            .filter(ablIdConst -> ablIdConst != null && allHeroAbilities.contains(ablIdConst.getName()))
            .map(ablIdConst -> Pair.of(ablIdConst, allAbilities.get(ablIdConst.getName())))
            .collect(Collectors.toSet());

    playerAbilities.forEach(ability -> addAbility(ability, matchDomain, playerDomain, playerSlot));
    return playerAbilities;
  }

  private void processItems(MatchResponsePlayersInner player, MatchDomain matchDomain,
                           PlayerDomain playerDomain, PlayerMatchStatisticDomain stats) {
    Map<String, ItemConstant> itemsConstant = constantManagers.getItemConstantMap();
    Long playerSlot = Long.valueOf(player.getPlayerSlot());

    Collection<MatchResponsePlayersInnerPurchaseLogInner> purchaseLogs =
        CollectionUtils.emptyIfNull(player.getPurchaseLog());
    stats.setDotaApiItems(!purchaseLogs.isEmpty());

    purchaseLogs.forEach(item -> addItemPurchase(item, itemsConstant, matchDomain, playerDomain, playerSlot));
    stats.setHasItems(!purchaseLogs.isEmpty());

    Collection<MatchResponsePlayersInnerNeutralItemHistoryInner> neutralItemHistory =
        CollectionUtils.emptyIfNull(player.getNeutralItemHistory());
    neutralItemHistory.forEach(item -> addNeutralItem(item, itemsConstant, matchDomain, playerDomain, playerSlot));
    stats.setHasNeutralItems(!neutralItemHistory.isEmpty());
  }

  /**
   * Adds an ability to the database.
   * Uses playerSlot as part of the primary key since anonymous players have playerId = -1.
   */
  private AbilityDomain addAbility(
      Pair<AbilityIdsConstant, AbilityConstant> abilityPair,
      MatchDomain matchDomain,
      PlayerDomain playerDomain,
      Long playerSlot) {

    AbilityDomain abilityDomain = new AbilityDomain();
    abilityDomain.setPlayerId(playerDomain.getId());
    abilityDomain.setPlayerSlot(playerSlot);
    abilityDomain.setMatchId(matchDomain.getId());

    AbilityIdsConstant id = abilityPair.getFirst();
    AbilityConstant name = abilityPair.getSecond();

    abilityDomain.setAbilityId(id.getId());
    abilityDomain.setName(id.getName());
    abilityDomain.setPrettyName(name.getDname());

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
      Long playerSlot) {
    String itemName = item.getItemNeutral();

    if (itemName == null) {
      log.debug("{} itemName is null", ProcessContext.getContextString());
      return null;
    }

    Long time = item.getTime();
    String itemEnhancement = item.getItemNeutralEnhancement();
    return saveItemDomain(itemsConstant, matchDomain, playerDomain, playerSlot, itemName, time, itemEnhancement);
  }

  private ItemDomain addItemPurchase(
      MatchResponsePlayersInnerPurchaseLogInner item,
      Map<String, ItemConstant> itemsConstant,
      MatchDomain matchDomain,
      PlayerDomain playerDomain,
      Long playerSlot) {
    String itemName = item.getKey();
    Long time = item.getTime();

    return saveItemDomain(itemsConstant, matchDomain, playerDomain, playerSlot, itemName, time, null);
  }

  /**
   * Saves an item to the database.
   * Uses playerSlot as part of the primary key since anonymous players have playerId = -1.
   */
  private ItemDomain saveItemDomain(
      Map<String, ItemConstant> itemsConstant,
      MatchDomain matchDomain,
      PlayerDomain playerDomain,
      Long playerSlot,
      String itemName,
      Long time,
      String neutralEnhancement) {
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

    return itemRepository.save(itemDomain);
  }
}
