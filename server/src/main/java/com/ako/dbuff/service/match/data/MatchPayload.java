package com.ako.dbuff.service.match.data;

import com.ako.dbuff.dotapi.model.MatchResponsePlayersInner;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MatchPayload {

  static final long ANONYMOUS = -1L;

  static Long playerIdOf(MatchResponsePlayersInner player) {
    return Optional.ofNullable(player.getAccountId()).orElse(ANONYMOUS);
  }

  static <T> T at(List<T> values, int index) {
    return values != null && index < values.size() ? values.get(index) : null;
  }

  /** The payload types these as bare objects, which Jackson materialises as maps. */
  @SuppressWarnings("unchecked")
  static Map<String, Object> asMap(Object value) {
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Collections.emptyMap();
  }

  static Long asLong(Object value) {
    return value instanceof Number number ? number.longValue() : null;
  }

  static String asString(Object value) {
    return value instanceof String string ? string : null;
  }
}
