package com.ako.dbuff.service.match.data;

import com.ako.dbuff.dao.model.MatchDomain;
import com.ako.dbuff.dao.model.WardEventDomain;
import com.ako.dbuff.dao.model.WardKind;
import com.ako.dbuff.dao.model.WardRemovalKind;
import com.ako.dbuff.dao.repo.WardEventRepo;
import com.ako.dbuff.dotapi.model.MatchResponsePlayersInner;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

/**
 * A ward row is a placement from {@code obs_log} or {@code sen_log} joined with the entry of the
 * same kind in {@code obs_left_log} or {@code sen_left_log} that has the same entity handle and the
 * earliest time not before the placement (the engine may reuse a handle later in the game).
 */
@Component
public class WardEventMapper extends PlayerLogMapper<WardEventDomain> {

  /** A ward that lives this close to its full life is taken to have expired. */
  static final long EXPIRY_TOLERANCE_SECONDS = 2;

  private static final Comparator<WardEventDomain> PLACEMENT_ORDER =
      Comparator.comparing(WardEventDomain::getPlacedTime)
          .thenComparing(WardEventDomain::getWardKind)
          .thenComparing(WardEventDomain::getX, Comparator.nullsFirst(Comparator.naturalOrder()))
          .thenComparing(WardEventDomain::getY, Comparator.nullsFirst(Comparator.naturalOrder()))
          .thenComparing(
              WardEventDomain::getEhandle, Comparator.nullsFirst(Comparator.naturalOrder()));

  private record WardKey(WardKind kind, Long ehandle) {}

  public WardEventMapper(WardEventRepo repo) {
    super(repo);
  }

  @Override
  List<WardEventDomain> rowsOf(MatchResponsePlayersInner player, MatchDomain match) {
    List<WardEventDomain> wards = new ArrayList<>();
    addPlacements(wards, player.getObsLog(), WardKind.OBSERVER);
    addPlacements(wards, player.getSenLog(), WardKind.SENTRY);
    wards.sort(PLACEMENT_ORDER);
    Map<WardKey, List<WardEventDomain>> byKey = new HashMap<>();
    for (int i = 0; i < wards.size(); i++) {
      WardEventDomain ward = wards.get(i);
      ward.setMatchId(match.getId());
      ward.setPlayerSlot(player.getPlayerSlot());
      ward.setPlayerId(MatchPayload.playerIdOf(player));
      ward.setWardIndex(i);
      if (ward.getEhandle() != null) {
        byKey
            .computeIfAbsent(
                new WardKey(ward.getWardKind(), ward.getEhandle()), k -> new ArrayList<>())
            .add(ward);
      }
    }
    attachRemovals(byKey, player.getObsLeftLog(), WardKind.OBSERVER);
    attachRemovals(byKey, player.getSenLeftLog(), WardKind.SENTRY);
    return wards;
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

  private static void attachRemovals(
      Map<WardKey, List<WardEventDomain>> placements, List<Object> log, WardKind wardKind) {
    List<Map<String, Object>> removals = new ArrayList<>();
    CollectionUtils.emptyIfNull(log).forEach(entry -> removals.add(MatchPayload.asMap(entry)));
    removals.sort(
        Comparator.comparing(
            removal -> MatchPayload.asLong(removal.get("time")),
            Comparator.nullsLast(Comparator.naturalOrder())));
    for (Map<String, Object> removal : removals) {
      Long handle = MatchPayload.asLong(removal.get("ehandle"));
      Long time = MatchPayload.asLong(removal.get("time"));
      if (handle == null || time == null) {
        continue;
      }
      List<WardEventDomain> candidates = placements.get(new WardKey(wardKind, handle));
      if (candidates == null) {
        continue;
      }
      WardEventDomain match = null;
      for (WardEventDomain ward : candidates) {
        if (ward.getRemovedTime() == null && ward.getPlacedTime() <= time) {
          match = ward;
        }
      }
      if (match != null) {
        match.setRemovedTime(time);
        match.setRemovedBy(MatchPayload.asString(removal.get("attackername")));
        match.setRemovalKind(removalKindOf(match));
      }
    }
  }

  private static WardRemovalKind removalKindOf(WardEventDomain ward) {
    long lived = ward.getRemovedTime() - ward.getPlacedTime();
    return lived >= ward.getWardKind().getLifetimeSeconds() - EXPIRY_TOLERANCE_SECONDS
        ? WardRemovalKind.EXPIRED
        : WardRemovalKind.KILLED;
  }
}
