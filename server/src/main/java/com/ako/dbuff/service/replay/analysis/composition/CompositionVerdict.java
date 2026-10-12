package com.ako.dbuff.service.replay.analysis.composition;

import com.ako.dbuff.service.replay.analysis.role.RoleFunction;
import java.util.List;
import java.util.stream.Collectors;

/**
 * What the rules say about one team at one stage.
 *
 * @param tokens the findings, such as {@code CARRIES_3} or {@code NO_INITIATOR}
 * @param unknown the functions that could not be judged because no profile of the team knew them
 */
public record CompositionVerdict(List<String> tokens, List<RoleFunction> unknown) {

  /** The tokens in plain words, then the unknown functions. */
  public String summary() {
    String found =
        tokens.isEmpty()
            ? (unknown.isEmpty() ? "no gaps found" : "nothing found")
            : tokens.stream().map(CompositionWords::token).collect(Collectors.joining("; "));
    if (unknown.isEmpty()) {
      return found;
    }
    return found
        + ". Unknown: "
        + unknown.stream().map(CompositionWords::function).collect(Collectors.joining(", "));
  }
}
