package com.ako.dbuff.service.match.mapper;

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

  public void handle(MatchDomain matchDomain, MatchResponsePlayersInner player) {

    Long accountId = Optional.ofNullable(player.getAccountId()).orElse(-1L);
    String personaname = Optional.ofNullable(player.getPersonaname()).orElse("anonymous");

    PlayerDomain playerDomain =
        playerRepo
            .findById(accountId)
            .orElseGet(() -> playerRepo.save(new PlayerDomain(accountId, personaname)));

    PlayerMatchStatisticDomain playerStatisticDomain = new PlayerMatchStatisticDomain();
    playerStatisticDomain.setPlayerId(accountId);
    playerStatisticDomain.setMatchId(matchDomain.getId());
    playerStatisticDomain.setPlayerSlot(player.getPlayerSlot());
    playerStatisticDomain.setObsPlaced(player.getObsPlaced());
    playerStatisticDomain.setSenPlaced(player.getSenPlaced());
    playerStatisticDomain.setCreepsStacked(player.getCreepsStacked());
    playerStatisticDomain.setLastHits(player.getLastHits());
    playerStatisticDomain.setDenies(player.getDenies());
    playerStatisticDomain.setCampsStacked(player.getCampsStacked());
    playerStatisticDomain.setRunePickups(player.getRunePickups());
    playerStatisticDomain.setTowerKills(player.getTowerKills());
    playerStatisticDomain.setRoshanKills(player.getRoshanKills());

    player.setPartySize(player.getPartySize());

    Long heroId = player.getHeroId();

    Map<String, HeroConstant> heroConstantMap = constantManagers.getHeroConstantMap();

    HeroConstant hero = heroConstantMap.get(String.valueOf(heroId));

    playerStatisticDomain.setHeroId(heroId);
    playerStatisticDomain.setHeroName(hero.getName());
    playerStatisticDomain.setHeroPrettyName(hero.getLocalized_name());

    playerStatisticDomain.setAganim(player.getAghanimsScepter());
    playerStatisticDomain.setAganimShard(player.getAghanimsShard());
    playerStatisticDomain.setMoonshard(player.getMoonshard());

    playerStatisticDomain.setWin(player.getWin());
    playerStatisticDomain.setTotalGold(player.getTotalGold());
    playerStatisticDomain.setTotalXp(player.getTotalXp());
    playerStatisticDomain.setKda(player.getKda());

    playerStatisticDomain.setAbandons(player.getAbandons());

    playerStatisticDomain.setNeutralKills(player.getNeutralKills());
    playerStatisticDomain.setCourierKills(player.getCourierKills());

    playerStatisticDomain.setLane(player.getLane());
    playerStatisticDomain.setLaneEfficiency(player.getLaneEfficiency());
    playerStatisticDomain.setLaneEfficiencyPct(player.getLaneEfficiencyPct());

    Map<String, Map<String, Integer>> benchmarks =
        (Map<String, Map<String, Integer>>) player.getBenchmarks();
    setUpBenchmarks(benchmarks, playerStatisticDomain);

    Set<String> allHeroAbilities = constantManagers.getAllHeroAbilities();

    Map<String, AbilityConstant> allAbilities = constantManagers.getAllAbilityConstants();

    Map<String, AbilityIdsConstant> abilityIdsConstantMap =
        constantManagers.getAbilityConstantMap();
    Set<Pair<AbilityIdsConstant, AbilityConstant>> playerAbilities =
        new HashSet<>(CollectionUtils.emptyIfNull(player.getAbilityUpgradesArr()))
            .stream()
                .map(abilityId -> abilityIdsConstantMap.get(String.valueOf(abilityId)))
                .filter(ablIdConst -> allHeroAbilities.contains(ablIdConst.getName()))
                .map(ablIdConst -> Pair.of(ablIdConst, allAbilities.get(ablIdConst.getName())))
                .collect(Collectors.toSet());

    playerAbilities.forEach(
        ability -> {
          addAbility(ability, matchDomain, playerDomain);
        });

    playerStatisticDomain.setHasAbilities(!playerAbilities.isEmpty());

    Map<String, ItemConstant> itemsConstant = constantManagers.getItemConstantMap();

    Collection<MatchResponsePlayersInnerPurchaseLogInner> purchaseLogs =
        CollectionUtils.emptyIfNull(player.getPurchaseLog());
    playerStatisticDomain.setDotaApiItems(!purchaseLogs.isEmpty());

    purchaseLogs.forEach(item -> addItemPurchase(item, itemsConstant, matchDomain, playerDomain));

    playerStatisticDomain.setHasItems(!purchaseLogs.isEmpty());

    Collection<MatchResponsePlayersInnerNeutralItemHistoryInner> neutralItemHistory =
        CollectionUtils.emptyIfNull(player.getNeutralItemHistory());
    CollectionUtils.emptyIfNull(player.getNeutralItemHistory())
        .forEach(item -> addNeutralItem(item, itemsConstant, matchDomain, playerDomain));

    playerStatisticDomain.setHasNeutralItems(!neutralItemHistory.isEmpty());

    playerGameStatisticRepo.save(playerStatisticDomain);
  }

  private AbilityDomain addAbility(
      Pair<AbilityIdsConstant, AbilityConstant> abilityPair,
      MatchDomain matchDomain,
      PlayerDomain playerDomain) {

    AbilityDomain abilityDomain = new AbilityDomain();
    abilityDomain.setPlayerId(playerDomain.getId());
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
      PlayerDomain playerDomain) {
    String itemName = item.getItemNeutral();

    if (itemName == null) {
      log.debug("itemName is null");
      return null;
    }

    Long time = item.getTime();
    String itemEnhancement = item.getItemNeutralEnhancement();
    return saveItemDomain(
        itemsConstant, matchDomain, playerDomain, itemName, time, itemEnhancement);
  }

  private ItemDomain addItemPurchase(
      MatchResponsePlayersInnerPurchaseLogInner item,
      Map<String, ItemConstant> itemsConstant,
      MatchDomain matchDomain,
      PlayerDomain playerDomain) {
    String itemName = item.getKey();
    Long time = item.getTime();

    return saveItemDomain(itemsConstant, matchDomain, playerDomain, itemName, time, null);
  }

  private ItemDomain saveItemDomain(
      Map<String, ItemConstant> itemsConstant,
      MatchDomain matchDomain,
      PlayerDomain playerDomain,
      String itemName,
      Long time,
      String neutralEnhancement) {
    ItemConstant itemConstant = itemsConstant.get(itemName);

    String dname = itemConstant.getDname();
    Long id = itemConstant.getId();

    ItemDomain itemDomain = new ItemDomain();
    itemDomain.setItemId(id);
    itemDomain.setMatchId(matchDomain.getId());
    itemDomain.setPlayerId(playerDomain.getId());
    itemDomain.setItemName(itemName);
    itemDomain.setItemPrettyName(dname);
    itemDomain.setItemPurchaseTime(time);

    itemDomain.setNeutral(neutralEnhancement != null);
    itemDomain.setNeutralEnhancement(neutralEnhancement);

    return itemRepository.save(itemDomain);
  }
}
