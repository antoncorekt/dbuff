package com.ako.dbuff.service.replay.report;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The wording of each known main aspect. Rows stored before the aspects (kind magic-stick,
 * hand-of-midas or farming-group) are still read with the wording of the aspect that replaced it.
 */
@Component
public class ItemKindWordings {
  private static final Map<String, String> LEGACY_KINDS =
      Map.of(
          "magic-stick", "charge-restore",
          "hand-of-midas", "midas-uses",
          "farming-group", "farm-rate");

  private final Map<String, ItemKindWording> byAspect;

  public ItemKindWordings(List<ItemKindWording> wordings) {
    this.byAspect =
        wordings.stream().collect(Collectors.toMap(ItemKindWording::aspect, Function.identity()));
  }

  /** The main aspects the whitelist has, for code that does not run in Spring. */
  public static ItemKindWordings standard() {
    return new ItemKindWordings(
        List.of(
            new StickKindWording(),
            new MidasKindWording(),
            new FarmingKindWording(),
            new CooldownKindWording()));
  }

  public Optional<ItemKindWording> find(String aspect) {
    return Optional.ofNullable(aspect).map(a -> byAspect.get(LEGACY_KINDS.getOrDefault(a, a)));
  }
}
