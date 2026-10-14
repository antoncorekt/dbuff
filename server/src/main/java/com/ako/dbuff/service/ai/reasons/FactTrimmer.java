package com.ako.dbuff.service.ai.reasons;

import com.ako.dbuff.config.OpenAiConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Keeps the facts within a token budget, dropping the lowest-weight ones first (the later of two
 * equal weights goes first). What is kept comes back in priority order, highest weight first.
 */
@Component
@RequiredArgsConstructor
public class FactTrimmer {
  private final OpenAiConfig openAi;

  /**
   * The facts that were kept and how many were dropped.
   *
   * @param kept the kept facts, highest weight first
   * @param dropped how many facts did not fit
   */
  public record Trimmed(List<Fact> kept, int dropped) {}

  public Trimmed trim(List<Fact> facts, int tokenBudget) {
    List<Fact> byLowestWeightFirst = new ArrayList<>(facts);
    byLowestWeightFirst.sort(
        Comparator.comparingInt(Fact::weight)
            .thenComparing(facts::indexOf, Comparator.reverseOrder()));
    int chars = facts.stream().mapToInt(f -> f.line().length() + 1).sum();
    Set<Fact> dropped = new HashSet<>();
    for (Fact fact : byLowestWeightFirst) {
      if (openAi.estimateTokens(chars) <= tokenBudget) {
        break;
      }
      chars -= fact.line().length() + 1;
      dropped.add(fact);
    }
    List<Fact> kept = new ArrayList<>(facts);
    kept.removeAll(dropped);
    kept.sort(Comparator.comparingInt(Fact::weight).reversed());
    return new Trimmed(List.copyOf(kept), dropped.size());
  }
}
