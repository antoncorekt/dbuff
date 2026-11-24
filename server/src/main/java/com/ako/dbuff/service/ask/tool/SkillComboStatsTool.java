package com.ako.dbuff.service.ask.tool;

import com.ako.dbuff.service.ask.AskContext;
import com.ako.dbuff.service.discord.command.PlayerReferenceResolver.ResolvedPlayer;
import com.ako.dbuff.service.ranking.AbilityRankingService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SkillComboStatsTool implements AskTool {

  private final AskToolArgs args;
  private final AbilityRankingService service;

  @Override
  public String name() {
    return "skill_combo_stats";
  }

  @Override
  public String description() {
    return "Games and win rate of one player when they have all the given skills in a game, optionally"
        + " together with items. Example: how often do I win with Blink and Refresher together?";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> props = new LinkedHashMap<>(PlayerToolSupport.commonProperties(true));
    props.put("skills", PlayerToolSupport.stringArray("Skill names, one or more."));
    props.put(
        "items", PlayerToolSupport.stringArray("Item names that must also be owned. Optional."));
    return PlayerToolSupport.schema(props, List.of("skills"));
  }

  @Override
  public Object call(JsonNode input, AskContext ctx) {
    Set<String> skills = args.stringSet(input, "skills");
    if (skills.isEmpty()) {
      throw new AskToolException("skills is required: give one or more skill names");
    }
    ResolvedPlayer player = ComboPlayers.single(args, input, ctx);
    PlayerToolSupport.Filters f = PlayerToolSupport.filters(args, input);
    return service.getAbilityComboStatistics(
        player.accountId(),
        f.from(),
        f.to(),
        skills,
        args.stringSet(input, "items"),
        f.heroes(),
        f.gameModes());
  }
}
