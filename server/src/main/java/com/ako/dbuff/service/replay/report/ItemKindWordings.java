package com.ako.dbuff.service.replay.report;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** The wording of each known item kind, by kind. */
@Component
public class ItemKindWordings {
  private final Map<String, ItemKindWording> byKind;

  public ItemKindWordings(List<ItemKindWording> wordings) {
    this.byKind =
        wordings.stream().collect(Collectors.toMap(ItemKindWording::kind, Function.identity()));
  }

  /** The three kinds the whitelist has, for code that does not run in Spring. */
  public static ItemKindWordings standard() {
    return new ItemKindWordings(
        List.of(new StickKindWording(), new MidasKindWording(), new FarmingKindWording()));
  }

  public Optional<ItemKindWording> find(String kind) {
    return Optional.ofNullable(kind).map(byKind::get);
  }
}
