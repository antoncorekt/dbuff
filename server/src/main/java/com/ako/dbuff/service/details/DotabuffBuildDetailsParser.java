package com.ako.dbuff.service.details;

import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.model.id.PlayerGameStatisticDomainId;
import com.ako.dbuff.dao.repo.AbilityRepo;
import com.ako.dbuff.dao.repo.ItemRepository;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.data.ItemConstant;
import java.util.Map;
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

  public void parse(Document doc, MatchDomain matchDomain) {
    {
      Elements section = doc.select("section.performance-artifact");

      if (section.isEmpty()) {
        log.error("Not found section.performance-artifact for matchId {}", matchDomain.getId());
        throw new IllegalStateException(
            "Not found section.performance-artifact. Game: "
                + matchDomain.getId()
                + " Doc is: "
                + doc.toString());
      }

      Map<String, ItemConstant> itemConstantMap = constantsManagers.getItemConstantMap();
      Map<String, String> abilitiesByDnName =
          constantsManagers.getAllAbilityConstants().entrySet().stream()
              .collect(
                  Collectors.toMap(x -> x.getValue().getDname(), Map.Entry::getKey, (x, y) -> y));
      Map<String, Long> abilityNameIdMap =
          constantsManagers.getAbilityConstantMap().entrySet().stream()
              .collect(
                  Collectors.toMap(
                      x -> x.getValue().getName(), x -> x.getValue().getId(), (x, y) -> y));

      for (Element elementSection : section) {
        Elements header = elementSection.select("header.header.no-padding");
        Elements titleHeader = header.first().select("div.title");
        Element linkToPlayer = titleHeader.first().select("a").first();

        if (linkToPlayer == null) {
          log.error("Not found linkToPlayer, matchId {}", matchDomain.getId());
          continue;
        }

        String href = linkToPlayer.attr("href");

        Long playerId = Long.valueOf(href.substring(href.lastIndexOf('/') + 1));

        //        if (!PlayerConfiguration.DEFAULT_PLAYERS.containsKey(playerId)) {
        //          continue;
        //        }

        PlayerMatchStatisticDomain playerStat =
            playerGameStatisticRepo
                .findById(
                    PlayerGameStatisticDomainId.builder()
                        .matchId(matchDomain.getId())
                        .playerId(playerId)
                        .build())
                .orElseGet(
                    () ->
                        playerGameStatisticRepo.save(
                            PlayerMatchStatisticDomain.builder()
                                .matchId(matchDomain.getId())
                                .playerId(playerId)
                                .build()));

        boolean parsedItems = false;
        Element itemsGroupElement = header.select("div.items").first();

        if (itemsGroupElement != null
            && playerStat.getHasItems() != null
            && !playerStat.getHasItems()) {
          log.info(
              "Found items 'div.items' for match: {}, player: {}", matchDomain.getId(), playerId);

          for (Element itemFrame : itemsGroupElement.children()) {
            parsedItems = true;
            Elements aElement = itemFrame.select("a");

            String attrHref = aElement.attr("href");
            String itemName = attrHref.substring(attrHref.lastIndexOf('/') + 1).replace("-", "_");

            ItemConstant itemConstant = itemConstantMap.get(itemName);

            if (itemConstant == null) {
              log.error("itemConstant not found, itemName:{}", itemName);
              continue;
            }

            ItemDomain itemDomain =
                ItemDomain.builder()
                    .matchId(matchDomain.getId())
                    .itemId(itemConstant.getId())
                    .itemName(itemName)
                    .itemPrettyName(itemConstant.getDname())
                    .playerId(playerId)
                    .build();

            Element timeElement = itemFrame.select("div.time").first();

            if (timeElement != null) {
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
            itemRepo.save(itemDomain);
          }

          playerStat.setHasItems(parsedItems);
        }

        playerGameStatisticRepo.save(playerStat);

        Element skills = elementSection.select("article.skill-choices").first();

        if (skills == null
            || playerStat.getHasAbilities() == null
            || playerStat.getHasAbilities()) {
          log.info(
              "SKILLS or ABILITIES NOT FOUND or skills already parsed, matchId: {}, playerId: {}",
              matchDomain.getId(),
              playerId);
          return;
        }

        for (Element skillEl : skills.children()) {
          String skillName = skillEl.select("div.icon").first().select("img").first().attr("alt");

          if (skillName.contains("Gold") || skillName.isEmpty()) {
            continue;
          }

          String abilityName = abilitiesByDnName.get(skillName.trim());

          if (abilityName == null) {
            log.error("abilityName not found, skillName:{}", skillName);
            continue;
          }

          Long abilityId = abilityNameIdMap.get(abilityName);

          if (abilityId == null) {
            log.error("abilityId not found, skillName:{}, abilityName: {}", skillName, abilityName);
            continue;
          }

          AbilityDomain abilityDomain =
              AbilityDomain.builder()
                  .matchId(matchDomain.getId())
                  .playerId(playerId)
                  .abilityId(abilityId)
                  .name(abilityName)
                  .prettyName(skillName)
                  .build();

          abilityRepo.save(abilityDomain);
        }

        playerStat.setHasAbilities(true);
        playerGameStatisticRepo.save(playerStat);
      }
    }
  }
}
