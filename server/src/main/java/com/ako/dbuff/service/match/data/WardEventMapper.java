package com.ako.dbuff.service.match.data;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.WardEventDomain;
import com.ako.dbuff.dao.model.WardKind;
import com.ako.dbuff.dao.model.WardRemovalKind;
import com.ako.dbuff.dao.repo.WardEventRepo;
import com.ako.dbuff.dotapi.model.MatchResponse;
import com.ako.dbuff.dotapi.model.MatchResponsePlayersInner;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

/**
 * A ward row is a placement from {@code obs_log} or {@code sen_log} joined with the {@code
 * *_left_log} entry that has the same entity handle and the earliest time not before the placement
 * (the engine may reuse a handle later in the game).
 */
@Component
@RequiredArgsConstructor
public class WardEventMapper implements MatchDetailMapper {

  /** A ward that lives this close to its full life is taken to have expired. */
  static final long EXPIRY_TOLERANCE_SECONDS = 2;

  private final WardEventRepo repo;

  @Override
  public void map(MatchResponse response, MatchDomain match) {
    List<WardEventDomain> rows = new ArrayList<>();
    for (MatchResponsePlayersInner player : CollectionUtils.emptyIfNull(response.getPlayers())) {
      if (player.getPlayerSlot() == null) {
        continue;
      }
      List<WardEventDomain> wards = new ArrayList<>();
      addPlacements(wards, player.getObsLog(), WardKind.OBSERVER);
      addPlacements(wards, player.getSenLog(), WardKind.SENTRY);
      wards.sort(Comparator.comparing(WardEventDomain::getPlacedTime));
      attachRemovals(wards, removalsOf(player));
      for (int i = 0; i < wards.size(); i++) {
        WardEventDomain ward = wards.get(i);
        ward.setMatchId(match.getId());
        ward.setPlayerSlot(player.getPlayerSlot());
        ward.setPlayerId(MatchPayload.playerIdOf(player));
        ward.setWardIndex(i);
      }
      rows.addAll(wards);
    }
    repo.saveAll(rows);
  }

  @Override
  public void deleteByMatchId(long matchId) {
    repo.deleteByMatchId(matchId);
  }

  private static void addPlacements(
      List<WardEventDomain> into, List<Object> log, WardKind wardKind) {
    for (Object entry : CollectionUtils.emptyIfNull(log)) {
      Map<String, Object> event = MatchPayload.asMap(entry);
      if (MatchPayload.asLong(event.get("time")) == null) {
        continue;
      }
      into.add(
          WardEventDomain.builder()
              .wardKind(wardKind)
              .placedTime(MatchPayload.asLong(event.get("time")))
              .x(MatchPayload.asDouble(event.get("x")))
              .y(MatchPayload.asDouble(event.get("y")))
              .ehandle(MatchPayload.asLong(event.get("ehandle")))
              .removalKind(WardRemovalKind.UNKNOWN)
              .build());
    }
  }

  private static List<Map<String, Object>> removalsOf(MatchResponsePlayersInner player) {
    List<Map<String, Object>> removals = new ArrayList<>();
    CollectionUtils.emptyIfNull(player.getObsLeftLog())
        .forEach(entry -> removals.add(MatchPayload.asMap(entry)));
    CollectionUtils.emptyIfNull(player.getSenLeftLog())
        .forEach(entry -> removals.add(MatchPayload.asMap(entry)));
    removals.sort(
        Comparator.comparing(
            removal -> MatchPayload.asLong(removal.get("time")),
            Comparator.nullsLast(Comparator.naturalOrder())));
    return removals;
  }

  private static void attachRemovals(
      List<WardEventDomain> wards, List<Map<String, Object>> removals) {
    for (Map<String, Object> removal : removals) {
      Long handle = MatchPayload.asLong(removal.get("ehandle"));
      Long time = MatchPayload.asLong(removal.get("time"));
      if (handle == null || time == null) {
        continue;
      }
      wards.stream()
          .filter(
              ward ->
                  ward.getRemovedTime() == null
                      && handle.equals(ward.getEhandle())
                      && ward.getPlacedTime() <= time)
          .reduce((earlier, later) -> later)
          .ifPresent(
              ward -> {
                ward.setRemovedTime(time);
                ward.setRemovedBy(MatchPayload.asString(removal.get("attackername")));
                ward.setRemovalKind(removalKindOf(ward));
              });
    }
  }

  private static WardRemovalKind removalKindOf(WardEventDomain ward) {
    long lived = ward.getRemovedTime() - ward.getPlacedTime();
    return lived >= ward.getWardKind().getLifetimeSeconds() - EXPIRY_TOLERANCE_SECONDS
        ? WardRemovalKind.EXPIRED
        : WardRemovalKind.KILLED;
  }
}
