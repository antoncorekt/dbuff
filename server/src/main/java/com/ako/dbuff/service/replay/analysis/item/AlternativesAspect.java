package com.ako.dbuff.service.replay.analysis.item;

import com.ako.dbuff.service.replay.analysis.item.AlternativeFinder.Alternative;
import com.ako.dbuff.service.replay.analysis.item.AlternativeFinder.Purchase;
import com.ako.dbuff.service.replay.analysis.item.AlternativeFinder.Result;
import com.ako.dbuff.service.replay.analysis.item.AlternativeFinder.Settings;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiency.Detail;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Worth nothing by itself: it names the items the same hero in the same role usually bought for the
 * same gold at the same time, one {@code ALTERNATIVE} row each for the two most bought, with their
 * win rate and net worth lead against the item's own where both sides have enough games.
 */
@Component
@RequiredArgsConstructor
public class AlternativesAspect implements ItemAspect {
  public static final String NAME = "alternatives";
  public static final String ROW = "ALTERNATIVE";
  public static final String GAMES = "games";
  public static final String WIN_RATE_DELTA = "winRateDelta";
  public static final String LEAD_DELTA = "leadDelta";

  private static final int SHOWN = 2;

  private final AlternativeFinder finder;
  private final ItemEfficiencyFile file;

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public Set<String> parameterNames() {
    return Set.of();
  }

  @Override
  public AspectPart measure(AspectContext context) {
    Long cost = context.cost();
    Result result =
        cost == null || context.hero().matchId() == 0
            ? new Result(0, List.of())
            : finder.find(
                new Purchase(
                    context.hero().matchId(),
                    context.hero().slot(),
                    HeroInputs.itemKey(context.held().getFirst()),
                    Set.copyOf(context.entry().items()),
                    context.bought(),
                    cost,
                    context.entry().alternatives()),
                Settings.of(file.alternativeSettings()));
    List<Detail> rows = new ArrayList<>();
    for (Alternative alternative : result.alternatives().stream().limit(SHOWN).toList()) {
      rows.add(row(alternative, context.bought()));
    }
    Map<String, Double> figures = new LinkedHashMap<>();
    figures.put("altOwnGames", (double) result.games());
    figures.put("altCandidates", (double) result.alternatives().size());
    return new AspectPart(0, true, figures, List.of(), rows, 0, null, null);
  }

  private static Detail row(Alternative alternative, double seconds) {
    Map<String, Double> figures = new LinkedHashMap<>();
    figures.put(GAMES, (double) alternative.games());
    if (alternative.winRateDelta() != null) {
      figures.put(WIN_RATE_DELTA, alternative.winRateDelta());
    }
    if (alternative.leadDelta() != null) {
      figures.put(LEAD_DELTA, alternative.leadDelta());
    }
    return new Detail(ROW, seconds, alternative.itemKey(), figures);
  }
}
