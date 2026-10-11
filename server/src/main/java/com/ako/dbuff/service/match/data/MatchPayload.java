package com.ako.dbuff.service.match.data;

import com.ako.dbuff.dotapi.model.MatchResponsePlayersInner;
import java.util.List;
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
}
