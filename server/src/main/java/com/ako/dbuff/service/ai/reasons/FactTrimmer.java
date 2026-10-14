package com.ako.dbuff.service.ai.reasons;

import com.ako.dbuff.config.OpenAiConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Keeps the facts within a token budget, dropping the lowest-weight ones first (the later of two
 * equal weights goes first). What is kept comes back in priority order, highest weight first, equal
 * weights in their given order.
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
    List<Integer> lowestFirst =
        IntStream.range(0, facts.size())
            .boxed()
            .sorted(
                Comparator.comparingInt((Integer i) -> facts.get(i).weight())
                    .thenComparing(Comparator.reverseOrder()))
            .toList();
    int chars = facts.stream().mapToInt(f -> f.line().length() + 1).sum();
    boolean[] dropped = new boolean[facts.size()];
    int droppedCount = 0;
    for (int index : lowestFirst) {
      if (openAi.estimateTokens(chars) <= tokenBudget) {
        break;
      }
      chars -= facts.get(index).line().length() + 1;
      dropped[index] = true;
      droppedCount++;
    }
    List<Fact> kept = new ArrayList<>();
    for (int i = 0; i < facts.size(); i++) {
      if (!dropped[i]) {
        kept.add(facts.get(i));
      }
    }
    kept.sort(Comparator.comparingInt(Fact::weight).reversed());
    return new Trimmed(List.copyOf(kept), droppedCount);
  }
}
