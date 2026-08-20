package com.ako.dbuff.service.ranking;

import java.util.Set;
import lombok.Getter;

/**
 * Thrown when a caller supplies item, hero, or ability names that match no known constant.
 *
 * <p>Extends {@link IllegalArgumentException} so existing REST error handling continues to treat it
 * as a client error. Carries the offending names so callers can render a useful message — the
 * Discord handlers use them to offer "did you mean" suggestions.
 *
 * <p>Exists because the previous behaviour was to log a warning and drop unknown names, which
 * turned a filtered query into an unfiltered one and reported statistics for a different question
 * than the one asked.
 */
@Getter
public class UnknownConstantNameException extends IllegalArgumentException {

  private final String constantType;
  private final Set<String> unknownNames;

  public UnknownConstantNameException(String constantType, Set<String> unknownNames) {
    super("Unknown " + constantType + ": " + String.join(", ", unknownNames));
    this.constantType = constantType;
    this.unknownNames = unknownNames;
  }
}
