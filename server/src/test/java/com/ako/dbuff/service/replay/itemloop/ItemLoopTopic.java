package com.ako.dbuff.service.replay.itemloop;

import com.ako.dbuff.dao.repo.ItemEfficiencyRepo;
import com.ako.dbuff.dao.repo.MatchRepo;
import com.ako.dbuff.service.replay.analysis.item.ItemEfficiencyJson;
import com.ako.dbuff.service.replay.analysis.item.ItemKits;
import com.ako.dbuff.service.replay.itemloop.ItemLoopReport.MatchData;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopDiff.Section;
import com.ako.dbuff.service.replay.looprunner.ReviewLoopTopic;
import com.ako.dbuff.service.replay.mode.GameModeProfile;
import com.ako.dbuff.service.replay.mode.StoredMatchModes;
import com.ako.dbuff.service.replay.report.ReportHeroNames;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The items learning loop: what the catalog items gave each hero, and each use or payback behind
 * it.
 */
@Component
class ItemLoopTopic implements ReviewLoopTopic {
  static final Path MATCH_LIST = Path.of("src/test/resources/replay/item-loop/matches.txt");
  static final List<Section> SECTIONS =
      List.of(new Section(ItemLoopReport.ITEMS, "", 1), new Section(ItemLoopReport.DETAILS, "", 1));

  private final StoredMatchModes matchModes;
  private final MatchRepo matchRepo;
  private final ReportHeroNames heroNames;
  private final ItemEfficiencyRepo repo;
  private final ItemKits kits;

  ItemLoopTopic(
      StoredMatchModes matchModes,
      MatchRepo matchRepo,
      ReportHeroNames heroNames,
      ItemEfficiencyRepo repo,
      ItemKits kits) {
    this.matchModes = matchModes;
    this.matchRepo = matchRepo;
    this.heroNames = heroNames;
    this.repo = repo;
    this.kits = kits;
  }

  @Override
  public String name() {
    return "items";
  }

  @Override
  public Path matchList() {
    return MATCH_LIST;
  }

  @Override
  public Path outputDir() {
    return Path.of("build", "item-loop");
  }

  @Override
  public List<Section> sections() {
    return SECTIONS;
  }

  @Override
  public String render(long matchId) {
    String mode =
        matchModes.activeFor(matchId).map(GameModeProfile::name).orElse("mode not active");
    return ItemLoopReport.render(
        new MatchData(
            matchId,
            matchRepo.findById(matchId).orElseThrow(),
            mode,
            heroNames.bySlot(matchId),
            repo.findByMatchId(matchId).stream().map(ItemEfficiencyJson::fromRow).toList(),
            key -> kits.item(key).map(i -> i.getDname()).orElse(null)));
  }
}
