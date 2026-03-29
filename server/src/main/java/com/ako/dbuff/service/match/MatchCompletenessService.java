package com.ako.dbuff.service.match;

import com.ako.dbuff.context.ProcessContext;
import com.ako.dbuff.dao.model.AbilityDomain;
import com.ako.dbuff.dao.model.ItemDomain;
import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.PlayerMatchStatisticDomain;
import com.ako.dbuff.dao.repo.AbilityRepo;
import com.ako.dbuff.dao.repo.ItemRepository;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.dao.repo.PlayerGameStatisticRepo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@AllArgsConstructor
@Slf4j
@Service
public class MatchCompletenessService {

  private final MatchRepo matchRepo;
  private final PlayerGameStatisticRepo playerGameStatisticRepo;
  private final ItemRepository itemRepository;
  private final AbilityRepo abilityRepo;

  @Transactional(readOnly = true)
  public List<Map.Entry<Long, Map<String, Object>>> findIncompletedMatches() {

    List<Map.Entry<Long, Map<String, Object>>> resList = new ArrayList<>();

    try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {

      // get result in stream
      var completionService =
          new ExecutorCompletionService<Map.Entry<Long, Map<String, Object>>>(executor);

      ScopedValue.where(ProcessContext.PROCESS_TYPE, "MatchCompleteness")
          .run(
              () -> {
                long totalTasks;
                try (Stream<MatchDomain> matchDomainStream = matchRepo.findAllStream()) {
                  totalTasks =
                      matchDomainStream
                          .peek(
                              matchDomain -> {
                                completionService.submit(
                                    () ->
                                        ScopedValue.where(
                                                ProcessContext.MATCH_ID, matchDomain.getId())
                                            .call(
                                                () ->
                                                    Map.entry(
                                                        matchDomain.getId(),
                                                        matchCheck(matchDomain))));
                              })
                          .count();
                }

                for (int i = 0; i < totalTasks; i++) {
                  try {
                    // take() блокирует поток, пока не появится ПЕРВЫЙ готовый результат
                    Future<Map.Entry<Long, Map<String, Object>>> future = completionService.take();
                    Map.Entry<Long, Map<String, Object>> result = future.get();

                    if (notCompleted(result.getValue())) {
                      resList.add(result);
                      log.warn("match {} is not completed", result.getKey());
                    } else {
                      log.info("match {} completed ok", result.getKey());
                    }

                    // Здесь мы сразу сохраняем в БД, логгируем или отправляем в Kafka
                    // Память не забивается всем списком результатов!

                  } catch (Exception e) {
                    log.error("Error processing task", e);
                  }
                }
              });
    }

    return resList;
  }

  private boolean notCompleted(Map<String, Object> result) {
    return (Boolean) result.get("hasEndProcess") == false
        || (Boolean) result.get("hasError")
        || !(Boolean) result.get("hasPlayerStats");
  }

  private Map<String, Object> matchCheck(MatchDomain matchDomain) {

    Map<String, Object> res = new HashMap<>();

    res.put("hasEndProcess", matchDomain.getEndProcess() != null);
    res.put("hasError", matchDomain.getError() != null);
    res.put("dotaApiFailed", matchDomain.getDotaApiFailed());
    res.put("abadon", matchDomain.getAbadon());
    res.put("date", matchDomain.getStartLocalDate());

    List<PlayerMatchStatisticDomain> statMyMatch =
        playerGameStatisticRepo.findAllByMatchId(matchDomain.getId());

    res.put("hasPlayerStats", !statMyMatch.isEmpty());
    res.put("statsPlayersCount", statMyMatch.size());

    List<ItemDomain> items = itemRepository.findAllByMatchId(matchDomain.getId());

    res.put("hasItems", !items.isEmpty());
    res.put("itemsPlayersCount", items.size());
    res.put(
        "hasAtLeastOneTime",
        items.stream().anyMatch(itemDomain -> itemDomain.getItemPurchaseTime() != null));
    res.put(
        "hasAllTime",
        items.stream().allMatch(itemDomain -> itemDomain.getItemPurchaseTime() != null));

    Map<Long, List<ItemDomain>> itemsPerPlayerSlots =
        items.stream().collect(Collectors.groupingBy(ItemDomain::getPlayerSlot));

    res.put("itemsPlayersCount", itemsPerPlayerSlots.size());

    List<AbilityDomain> abilities = abilityRepo.findAllByMatchId(matchDomain.getId());

    res.put("hasAbilities", !abilities.isEmpty());
    res.put("abilitiesPlayersCount", abilities.size());

    Map<Long, List<AbilityDomain>> abilitiesPerPlayerSlot =
        abilities.stream().collect(Collectors.groupingBy(AbilityDomain::getPlayerSlot));

    res.put("abilitiesPlayersCount", abilitiesPerPlayerSlot.size());

    log.info("checked match {}", matchDomain.getId());

    return res;
  }
}
