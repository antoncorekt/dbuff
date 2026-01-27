package com.ako.dbuff.service.details;

import com.ako.dbuff.context.ProcessContext;
import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.repo.AbilityRepo;
import com.ako.dbuff.dao.repo.ItemRepository;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.ItemConstant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class DotabuffBuildDetailsParser {

  private final ItemRepository itemRepo;
  private final AbilityRepo abilityRepo;
  private final ConstantsManagers constantsManagers;
  private final PlayerGameStatisticRepo playerGameStatisticRepo;

  private static final Map<String, String> KNOWN_ITEM_MAPPER;

  static {
    KNOWN_ITEM_MAPPER = new HashMap<>();
    KNOWN_ITEM_MAPPER.put("heart_of_tarrasque", "heart");
    KNOWN_ITEM_MAPPER.put("giants_maul", "giant_maul");
    KNOWN_ITEM_MAPPER.put("aghanims_scepter", "ultimate_scepter");
    KNOWN_ITEM_MAPPER.put("gleipnir", "gungir");
    KNOWN_ITEM_MAPPER.put("guardian_shell", "guardian_greaves");
    KNOWN_ITEM_MAPPER.put("crystalys", "lesser_crit");
    KNOWN_ITEM_MAPPER.put("dagon_level_5", "dagon_5");
    KNOWN_ITEM_MAPPER.put("dagon_level_4", "dagon_4");
    KNOWN_ITEM_MAPPER.put("dagon_level_3", "dagon_3");
    KNOWN_ITEM_MAPPER.put("dagon_level_2", "dagon_2");
    KNOWN_ITEM_MAPPER.put("dagon_level_1", "dagon_1");
    KNOWN_ITEM_MAPPER.put("vladmirs_offering", "vladmir");
    KNOWN_ITEM_MAPPER.put("euls_scepter_of_divinity", "cyclone");
    KNOWN_ITEM_MAPPER.put("tumblers_toy", "pogo_stick");
    KNOWN_ITEM_MAPPER.put("brigands_blade", "misericorde");
    KNOWN_ITEM_MAPPER.put("mysterious_hat", "fairys_trinket");
    KNOWN_ITEM_MAPPER.put("linkens_sphere", "sphere");
  }

  /**
   * Parses dotabuff page to extract items and abilities for players. Uses ScopedValue context for
   * logging - matchId should already be in scope.
   *
   * @param doc the scraped document
   * @param matchDomain the match domain
   */
  public void parse(Document doc, MatchDomain matchDomain) {
    String ctx = ProcessContext.getContextString();
    Elements section = doc.select("section.performance-artifact");

    if (section.isEmpty()) {
      log.error(
          "{} Not found section.performance-artifact for matchId {}", ctx, matchDomain.getId());
      throw new IllegalStateException(
          "Not found section.performance-artifact. Game: "
              + matchDomain.getId()
              + " Doc is: "
              + doc.toString());
    }

    Map<String, ItemConstant> itemConstantMap = constantsManagers.getItemConstantMap();
    Map<String, ItemConstant> itemsByDname = buildItemsByDnameMap(itemConstantMap);
    Map<String, String> abilitiesByDnName = buildAbilitiesByDnNameMap();
    Map<String, Long> abilityNameIdMap = buildAbilityNameIdMap();

    for (Element elementSection : section) {
      processPlayerSection(
          elementSection,
          matchDomain,
          itemConstantMap,
          itemsByDname,
          abilitiesByDnName,
          abilityNameIdMap);
    }
  }

  private Map<String, ItemConstant> buildItemsByDnameMap(
      Map<String, ItemConstant> itemConstantMap) {
    return itemConstantMap.entrySet().stream()
        .filter(e -> e.getValue().getDname() != null)
        .collect(
            Collectors.toMap(
                e -> e.getValue().getDname().toLowerCase(), Map.Entry::getValue, (x, y) -> x));
  }

  private Map<String, String> buildAbilitiesByDnNameMap() {
    return constantsManagers.getAllAbilityConstants().entrySet().stream()
        .collect(Collectors.toMap(x -> x.getValue().getDname(), Map.Entry::getKey, (x, y) -> y));
  }

  private Map<String, Long> buildAbilityNameIdMap() {
    return constantsManagers.getAbilityConstantMap().entrySet().stream()
        .collect(
            Collectors.toMap(x -> x.getValue().getName(), x -> x.getValue().getId(), (x, y) -> y));
  }

  private void processPlayerSection(
      Element elementSection,
      MatchDomain matchDomain,
      Map<String, ItemConstant> itemConstantMap,
      Map<String, ItemConstant> itemsByDname,
      Map<String, String> abilitiesByDnName,
      Map<String, Long> abilityNameIdMap) {

    Elements header = elementSection.select("header.header.no-padding");
    Elements titleHeader = header.first().select("div.title");
    Optional<Element> linkToPlayer = Optional.ofNullable(titleHeader.first().select("a").first());

    Optional<String> href = linkToPlayer.map(x -> x.attr("href"));
    Long playerId = href.map(x -> Long.valueOf(x.substring(x.lastIndexOf('/') + 1))).orElse(-1L);

    // Process player with playerId in scope for logging
    ProcessContext.runWithPlayerId(
        playerId,
        () ->
            processPlayerData(
                elementSection,
                header,
                matchDomain,
                playerId,
                itemConstantMap,
                itemsByDname,
                abilitiesByDnName,
                abilityNameIdMap));
  }

  private void processPlayerData(
      Element elementSection,
      Elements header,
      MatchDomain matchDomain,
      Long playerId,
      Map<String, ItemConstant> itemConstantMap,
      Map<String, ItemConstant> itemsByDname,
      Map<String, String> abilitiesByDnName,
      Map<String, Long> abilityNameIdMap) {

    String ctx = ProcessContext.getContextString();

    PlayerMatchStatisticDomain playerStat = getPlayerStat(matchDomain.getId(), playerId, header);

    if (playerStat == null) {
      log.warn(
          "{} Player stat not found for match: {}, player: {}. "
              + "Cannot save items/abilities without playerSlot from existing stat record.",
          ctx,
          matchDomain.getId(),
          playerId);
      // Cannot save items and abilities without playerSlot (required for PK)
      // The player must be processed by DotaAPI first to get playerSlot
      return;
    }

    Long playerSlot = playerStat.getPlayerSlot();

    boolean parsedItems =
        processItems(
            header, matchDomain, playerId, playerSlot, playerStat, itemConstantMap, itemsByDname);

    if (parsedItems) {
      playerStat.setHasItems(true);
    }
    playerGameStatisticRepo.save(playerStat);

    processAbilities(
        elementSection,
        matchDomain,
        playerId,
        playerSlot,
        playerStat,
        abilitiesByDnName,
        abilityNameIdMap);
  }

  /**
   * Gets or creates player statistics by matchId and playerId. Note: When creating new stats from
   * Dotabuff scraping, we don't have playerSlot, so we cannot create new records. This method only
   * returns existing records.
   *
   * @param matchId the match ID
   * @param playerId the player account ID
   * @return the player statistics if found, or null if not found
   */
  private PlayerMatchStatisticDomain getPlayerStat(Long matchId, Long playerId, Elements header) {

    // not anon
    if (playerId != -1) {
      return playerGameStatisticRepo.findByMatchIdAndPlayerId(matchId, playerId).orElse(null);
    }

    try {
      List<PlayerMatchStatisticDomain> allByMatchId =
          playerGameStatisticRepo.findAllByMatchId(matchId);

      Elements avatarContainer = header.first().select("div.avatar");
      Element linkToAvatar = avatarContainer.first().select("a").first();

      String href = linkToAvatar.attr("href");
      String hero = href.substring(href.lastIndexOf('/') + 1).replace("_", "").replace("-", "");

      log.info("Trying to parse hero {} for matchId: {}", hero, matchId);

      return allByMatchId.stream()
          .filter(x -> x.getHeroPrettyName().replace(" ", "").toLowerCase().equals(hero))
          .findAny()
          .orElseThrow(
              () ->
                  new IllegalStateException("Hero " + hero + " not found for matchId: " + matchId));
    } catch (Exception e) {
      log.error("Failed to parse hero for match: {}", matchId, e);
      return null;
    }
  }

  private boolean processItems(
      Elements header,
      MatchDomain matchDomain,
      Long playerId,
      Long playerSlot,
      PlayerMatchStatisticDomain playerStat,
      Map<String, ItemConstant> itemConstantMap,
      Map<String, ItemConstant> itemsByDname) {

    String ctx = ProcessContext.getContextString();
    Element itemsGroupElement = header.select("div.items").first();

    if (itemsGroupElement == null || playerStat.getHasItems() == null || playerStat.getHasItems()) {
      return false;
    }

    log.info(
        "{} Found items 'div.items' for match: {}, player: {}", ctx, matchDomain.getId(), playerId);

    boolean parsedItems = false;
    for (Element itemFrame : itemsGroupElement.children()) {
      parsedItems = true;
      processItemElement(
          itemFrame, matchDomain, playerId, playerSlot, itemConstantMap, itemsByDname);
    }
    return parsedItems;
  }

  /**
   * Processes an item element from the Dotabuff page. Uses playerSlot as part of the primary key
   * since anonymous players have playerId = -1.
   */
  private void processItemElement(
      Element itemFrame,
      MatchDomain matchDomain,
      Long playerId,
      Long playerSlot,
      Map<String, ItemConstant> itemConstantMap,
      Map<String, ItemConstant> itemsByDname) {

    String ctx = ProcessContext.getContextString();
    Elements aElement = itemFrame.select("a");
    String attrHref = aElement.attr("href");
    String itemName = attrHref.substring(attrHref.lastIndexOf('/') + 1).replace("-", "_");
    itemName = KNOWN_ITEM_MAPPER.getOrDefault(itemName, itemName);

    ItemConstant itemConstant = itemConstantMap.get(itemName);
    if (itemConstant == null) {
      itemConstant = itemsByDname.get(itemName.replace("_", " "));
    }

    if (itemConstant == null) {
      log.error("{} itemConstant not found, itemName: {}", ctx, itemName);
      return;
    }

    ItemDomain itemDomain =
        ItemDomain.builder()
            .matchId(matchDomain.getId())
            .itemId(itemConstant.getId())
            .itemName(itemName)
            .itemPrettyName(itemConstant.getDname())
            .playerId(playerId)
            .playerSlot(playerSlot)
            .build();

    Element timeElement = itemFrame.select("div.time").first();
    if (timeElement != null) {
      parseItemTime(timeElement, itemDomain);
    }

    itemRepo.save(itemDomain);
  }

  private void parseItemTime(Element timeElement, ItemDomain itemDomain) {
    String text = timeElement.text();
    if (text.contains("m")) {
      String min = text.replace("m", "").trim();
      itemDomain.setItemPurchaseTime(Long.parseLong(min) * 60);
    }
    if (text.contains(":")) {
      String[] split = text.split(":");
      Long sec = Long.parseLong(split[0]) * 60 + Long.parseLong(split[1]);
      itemDomain.setItemPurchaseTime(sec);
    }
  }

  private void processAbilities(
      Element elementSection,
      MatchDomain matchDomain,
      Long playerId,
      Long playerSlot,
      PlayerMatchStatisticDomain playerStat,
      Map<String, String> abilitiesByDnName,
      Map<String, Long> abilityNameIdMap) {

    String ctx = ProcessContext.getContextString();
    Element skills = elementSection.select("article.skill-choices").first();

    if (skills == null || playerStat.getHasAbilities() == null || playerStat.getHasAbilities()) {
      log.info(
          "{} SKILLS or ABILITIES NOT FOUND or skills already parsed, matchId: {}, playerId: {}",
          ctx,
          matchDomain.getId(),
          playerId);
      return;
    }

    for (Element skillEl : skills.children()) {
      processSkillElement(
          skillEl, matchDomain, playerId, playerSlot, abilitiesByDnName, abilityNameIdMap);
    }

    playerStat.setHasAbilities(true);
    playerGameStatisticRepo.save(playerStat);
  }

  /**
   * Processes a skill element from the Dotabuff page. Uses playerSlot as part of the primary key
   * since anonymous players have playerId = -1.
   */
  private void processSkillElement(
      Element skillEl,
      MatchDomain matchDomain,
      Long playerId,
      Long playerSlot,
      Map<String, String> abilitiesByDnName,
      Map<String, Long> abilityNameIdMap) {

    String ctx = ProcessContext.getContextString();
    String skillName = skillEl.select("div.icon").first().select("img").first().attr("alt");

    if (skillName.contains("Gold") || skillName.isEmpty()) {
      return;
    }

    String abilityName = abilitiesByDnName.get(skillName.trim());
    if (abilityName == null) {
      log.error("{} abilityName not found, skillName: {}", ctx, skillName);
      return;
    }

    Long abilityId = abilityNameIdMap.get(abilityName);
    if (abilityId == null) {
      log.error(
          "{} abilityId not found, skillName: {}, abilityName: {}", ctx, skillName, abilityName);
      return;
    }

    AbilityDomain abilityDomain =
        AbilityDomain.builder()
            .matchId(matchDomain.getId())
            .playerId(playerId)
            .playerSlot(playerSlot)
            .abilityId(abilityId)
            .name(abilityName)
            .prettyName(skillName)
            .build();

    abilityRepo.save(abilityDomain);
  }
}
