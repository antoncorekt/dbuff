package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.model.ItemEfficiencyDomain;
import com.ako.dbuff.dao.repo.ItemEfficiencyRepo;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiencyInputs.MatchInputs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Judges the items of {@code replay/items.yaml} for every hero who held one, with the aspects each
 * entry lists (see {@link ItemAspect}) summed into one row. It does not know any item: a new item
 * or group is a new entry, a new kind of judgment a new aspect bean. The OpenDota based purchase
 * report ({@code ItemEfficiencyAnalyzer} in the match report) is a different feature.
 */
@Slf4j
@Order(85)
@Component
@RequiredArgsConstructor
public class ReplayItemEfficiencyAnalyzer implements ReplayAnalyzer {
  private final ItemEfficiencyFile file;
  private final List<ItemAspect> aspects;
  private final ItemEfficiencyInputs inputs;
  private final ItemKits kits;
  private final ItemEfficiencyRepo repo;

  @Override
  public String name() {
    return "item-efficiency";
  }

  @Override
  public int version() {
    return 3;
  }

  @Override
  public Set<String> requires() {
    return Set.of(
        "players", "tracks", "state-changes", "combat-log", "activity", "creep-kills", "minutes");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    List<ItemEfficiencyEntry> entries = file.entries();
    List<List<ItemAspect>> chosen = aspectsFor(entries);
    deleteByMatchId(matchId);
    MatchInputs match = inputs.load(matchId);
    List<ItemEfficiency> results = new ArrayList<>();
    for (int i = 0; i < entries.size(); i++) {
      results.addAll(
          AspectSum.judge(
              entries.get(i),
              chosen.get(i),
              kits,
              match.gameEndSeconds(),
              match.heroes(),
              file.prices()));
    }
    Map<String, String> categories =
        entries.stream()
            .collect(Collectors.toMap(ItemEfficiencyEntry::key, ItemEfficiencyEntry::category));
    repo.saveAll(
        results.stream()
            .map(
                r -> {
                  ItemEfficiencyDomain row = ItemEfficiencyJson.toRow(matchId, r, version());
                  row.setCategory(categories.get(r.entryKey()));
                  return row;
                })
            .toList());
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    repo.deleteByMatchId(matchId);
  }

  /**
   * Every entry's aspects, or one error naming all the entries that list an unknown aspect or
   * misspell a parameter, so a typo in the file fails once and readably. An item the constants lack
   * is not an error: its rows carry COST_UNKNOWN, and one line names the entry and the item.
   */
  private List<List<ItemAspect>> aspectsFor(List<ItemEfficiencyEntry> entries) {
    Map<String, ItemAspect> byName =
        aspects.stream().collect(Collectors.toMap(ItemAspect::name, Function.identity()));
    List<String> problems = new ArrayList<>();
    List<List<ItemAspect>> chosen = new ArrayList<>();
    for (ItemEfficiencyEntry entry : entries) {
      List<ItemAspect> own = new ArrayList<>();
      Set<String> listed = new java.util.HashSet<>();
      for (String name : entry.aspects()) {
        ItemAspect aspect = byName.get(name);
        if (!listed.add(name)) {
          problems.add("entry '%s': aspect '%s' is listed twice".formatted(entry.key(), name));
        } else if (aspect == null) {
          problems.add("entry '%s': unknown aspect '%s'".formatted(entry.key(), name));
        } else {
          own.add(aspect);
        }
      }
      chosen.add(own);
      Set<String> known =
          own.stream().flatMap(a -> a.parameterNames().stream()).collect(Collectors.toSet());
      entry.parameters().keySet().stream()
          .sorted()
          .filter(name -> !known.contains(name))
          .forEach(
              name ->
                  problems.add(
                      "entry '%s': unknown parameter '%s' for aspects %s"
                          .formatted(entry.key(), name, entry.aspects())));
      entry.items().stream()
          .filter(item -> kits.cost(item).isEmpty())
          .forEach(
              item ->
                  log.warn(
                      "Item efficiency entry '{}': the item constants have no cost for '{}'",
                      entry.key(),
                      item));
    }
    if (!problems.isEmpty()) {
      throw new IllegalStateException("Item efficiency file: " + String.join("; ", problems));
    }
    return chosen;
  }
}
