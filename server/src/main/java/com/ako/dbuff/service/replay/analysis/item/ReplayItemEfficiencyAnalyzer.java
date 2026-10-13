package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.dao.repo.ItemEfficiencyRepo;
import com.ako.dbuff.service.replay.analysis.ReplayAnalyzer;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiencyInputs.MatchInputs;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Judges the items of {@code replay/item-efficiency.json} for every hero who held one, with the
 * evaluator that supports each entry (see {@link ItemEfficiencyEvaluator}). It does not know any
 * item: a new item or group is a new entry or a new evaluator bean. The OpenDota based purchase
 * report ({@code ItemEfficiencyAnalyzer} in the match report) is a different feature.
 */
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
    List<ItemEfficiencyEvaluator> chosen = entries.stream().map(this::evaluatorFor).toList();
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

  private ItemEfficiencyEvaluator evaluatorFor(ItemEfficiencyEntry entry) {
    ItemEfficiencyEvaluator evaluator =
        evaluators.stream()
            .filter(e -> e.supports(entry))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Item efficiency entry '%s': no evaluator for kind '%s'"
                            .formatted(entry.key(), entry.kind())));
    entry.parameters().keySet().stream()
        .filter(name -> !evaluator.parameterNames().contains(name))
        .findFirst()
        .ifPresent(
            name -> {
              throw new IllegalStateException(
                  "Item efficiency entry '%s': unknown parameter '%s' for kind '%s'"
                      .formatted(entry.key(), name, entry.kind()));
            });
    return evaluator;
  }
}
