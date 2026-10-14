package com.ako.dbuff.service.replay.report;

import com.ako.dbuff.dao.model.ObjectiveConversionDomain;
import com.ako.dbuff.service.replay.analysis.behaviour.RoshanCall;
import com.ako.dbuff.service.replay.analysis.building.BuildingName;
import com.ako.dbuff.service.replay.analysis.reason.Enums;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The words of the objectives after a won fight that the Discord section and the behaviour loop
 * report share.
 */
public final class ObjectiveWording {
  private ObjectiveWording() {}

  /** Who stood at Roshan's pit and whether taking him was expected or a gamble. */
  public static String roshanNote(ObjectiveConversionDomain r, Map<Long, String> heroNames) {
    String near =
        r.getRoshanNearSlots() == null
            ? "where the killer stood is not known"
            : r.getRoshanNearSlots().isBlank()
                ? "no winner hero was found at the pit"
                : Arrays.stream(r.getRoshanNearSlots().split(","))
                        .map(ObjectiveWording::slot)
                        .filter(Objects::nonNull)
                        .map(slot -> BehaviourWording.hero(heroNames, slot))
                        .collect(Collectors.joining(", "))
                    + " stood at the pit";
    RoshanCall call = Enums.find(RoshanCall.class, r.getRoshanCall());
    return near + "; " + (call == null ? "the state before the fight is not known" : call.words());
  }

  /** Why a missed fight has no tower to measure from. */
  public static String noTowerReason(ObjectiveConversionDomain r) {
    return r.getWinnersAlive() != null && r.getWinnersAlive() == 0
        ? "no hero of the winner was alive when the fight ended"
        : "no enemy lane tower was left to measure from";
  }

  /** A tower as {@code the top lane tier 1 tower}; {@code a tower} when its name is not known. */
  public static String tower(String stored, String lane) {
    return BuildingName.parse(stored)
        .filter(name -> name.tier() != null)
        .map(
            name ->
                "the %stier %d tower"
                    .formatted(
                        lane == null ? "" : BehaviourWording.words(lane) + " lane ", name.tier()))
        .orElse("a tower");
  }

  private static Long slot(String text) {
    try {
      return Long.parseLong(text.strip());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
