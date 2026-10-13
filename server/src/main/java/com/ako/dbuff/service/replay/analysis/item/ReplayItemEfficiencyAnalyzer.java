package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.repo.ItemEfficiencyRepo;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiencyInputs.MatchInputs;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Judges the items of {@code replay/item-efficiency.json} for every hero who held one, with the
 * evaluator that supports each entry (see {@link ItemEfficiencyEvaluator}). It does not know any
 * item: a new item or group is a new entry or a new evaluator bean. The OpenDota based purchase
 * report ({@code ItemEfficiencyAnalyzer} in the match report) is a different feature.
 */
@Slf4j
@Order(85)
@Component
@RequiredArgsConstructor
public class ReplayItemEfficiencyAnalyzer implements ReplayAnalyzer {
  private final ItemEfficiencyFile file;
  private final List<ItemEfficiencyEvaluator> evaluators;
  private final ItemEfficiencyInputs inputs;
  private final ItemKits kits;
  private final ItemEfficiencyRepo repo;

  @Override
  public String name() {
    return "item-efficiency";
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  public Set<String> requires() {
    return Set.of("players", "tracks", "state-changes", "combat-log", "activity", "creep-kills");
  }

  @Override
  @Transactional
  public void analyze(long matchId) {
    List<ItemEfficiencyEntry> entries = file.entries();
    List<ItemEfficiencyEvaluator> chosen = evaluatorsFor(entries);
    deleteByMatchId(matchId);
    MatchInputs match = inputs.load(matchId);
    List<ItemEfficiency> results = new ArrayList<>();
    for (int i = 0; i < entries.size(); i++) {
      results.addAll(
          chosen
              .get(i)
              .evaluate(
                  new EntryContext(entries.get(i), kits, match.gameEndSeconds(), match.heroes())));
    }
    repo.saveAll(
        results.stream().map(r -> ItemEfficiencyJson.toRow(matchId, r, version())).toList());
  }

  @Override
  @Transactional
  public void deleteByMatchId(long matchId) {
    repo.deleteByMatchId(matchId);
  }

  /**
   * Every entry's evaluator, or one error naming all the entries that have none or misspell a
   * parameter, so a typo in the file fails once and readably. An item the constants lack is not an
   * error: its rows carry COST_UNKNOWN, and one line names the entry and the item.
   */
  private List<ItemEfficiencyEvaluator> evaluatorsFor(List<ItemEfficiencyEntry> entries) {
    List<String> problems = new ArrayList<>();
    List<ItemEfficiencyEvaluator> chosen = new ArrayList<>();
    for (ItemEfficiencyEntry entry : entries) {
      ItemEfficiencyEvaluator evaluator =
          evaluators.stream().filter(e -> e.supports(entry)).findFirst().orElse(null);
      chosen.add(evaluator);
      if (evaluator == null) {
        problems.add("entry '%s': no evaluator for kind '%s'".formatted(entry.key(), entry.kind()));
        continue;
      }
      entry.parameters().keySet().stream()
          .sorted()
          .filter(name -> !evaluator.parameterNames().contains(name))
          .forEach(
              name ->
                  problems.add(
                      "entry '%s': unknown parameter '%s' for kind '%s'"
                          .formatted(entry.key(), name, entry.kind())));
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
