package com.ako.dbuff.service.ask.tool;

import com.ako.dbuff.dao.model.SkillPickStatDomain;
import com.ako.dbuff.service.ask.AskContext;
import com.ako.dbuff.service.constant.ConstantNameResolver;
import com.ako.dbuff.service.constant.ConstantsManagers;
import com.ako.dbuff.service.constant.NameResolution;
import com.ako.dbuff.service.constant.data.AbilityIdsConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillAnswer;
import com.ako.dbuff.service.replay.analysis.skillstat.SkillLookup;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SkillLookupTool implements AskTool {

  private final SkillLookup lookup;
  private final ConstantNameResolver names;
  private final ConstantsManagers constants;
  private final AskToolArgs args;

  /** One line of a skill answer: the subject or one of its details. */
  public record SkillRow(String name, long games, long wins, double winRate) {}

  /** The subject row and the rows that explain it. */
  public record SkillRows(SkillRow subject, List<SkillRow> details) {}

  @Override
  public String name() {
    return "skill_lookup";
  }

  @Override
  public String description() {
    return "Ability Draft skill statistics from all stored games. mode hero: a hero and, for the"
        + " given skills, how they did on it. mode skill: one skill and the hero it did best on."
        + " mode pair: two skills together. Names are display or internal names.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type",
        "object",
        "properties",
        Map.of(
            "mode",
            Map.of("type", "string", "enum", List.of("hero", "skill", "pair")),
            "hero",
            Map.of("type", "string", "description", "Hero name, for mode hero"),
            "skill",
            Map.of("type", "string", "description", "Skill name, for mode skill"),
            "skills",
            Map.of(
                "type",
                "array",
                "items",
                Map.of("type", "string"),
                "description",
                "Skill names: for mode pair exactly two, for mode hero the skills to check")),
        "required",
        List.of("mode"),
        "additionalProperties",
        false);
  }

  @Override
  public Object call(JsonNode input, AskContext ctx) {
    JsonNode mode = input == null ? null : input.get("mode");
    String value = mode == null ? "" : mode.asText().toLowerCase(Locale.ROOT);
    return switch (value) {
      case "hero" -> hero(input);
      case "skill" -> skill(input);
      case "pair" -> pair(input);
      default -> throw new AskToolException("mode must be one of hero, skill, pair");
    };
  }

  private SkillRows hero(JsonNode input) {
    long heroId = single(input, "hero", names::resolveHeroes, names::suggestHero);
    List<Long> skills =
        ids(args.stringSet(input, "skills"), names::resolveAbilities, names::suggestAbility);
    return rows(lookup.hero(heroId, skills));
  }

  private SkillRows skill(JsonNode input) {
    return rows(
        lookup.skill(single(input, "skill", names::resolveAbilities, names::suggestAbility)));
  }

  private SkillRows pair(JsonNode input) {
    List<Long> skills =
        ids(args.stringSet(input, "skills"), names::resolveAbilities, names::suggestAbility);
    if (skills.size() != 2) {
      throw new AskToolException("mode pair needs exactly two different skills in skills");
    }
    return rows(lookup.pair(skills.get(0), skills.get(1)));
  }

  private long single(
      JsonNode input,
      String field,
      Function<Set<String>, NameResolution> resolve,
      Function<String, java.util.Optional<String>> suggest) {
    Set<String> given = args.stringSet(input, field);
    if (given.size() != 1) {
      throw new AskToolException(field + " is required: exactly one name");
    }
    return ids(given, resolve, suggest).get(0);
  }

  private List<Long> ids(
      Set<String> given,
      Function<Set<String>, NameResolution> resolve,
      Function<String, java.util.Optional<String>> suggest) {
    NameResolution resolution = resolve.apply(given);
    if (resolution.hasUnresolved()) {
      List<String> messages = new ArrayList<>();
      for (String unknown : resolution.unresolvedNames()) {
        messages.add(
            suggest
                .apply(unknown)
                .map(s -> unknown + " (did you mean " + s + "?)")
                .orElse(unknown));
      }
      throw new AskToolException("Unknown name: " + String.join(", ", messages));
    }
    return new ArrayList<>(resolution.resolvedIds());
  }

  private SkillRows rows(SkillAnswer answer) {
    return new SkillRows(row(answer.subject()), answer.details().stream().map(this::row).toList());
  }

  private SkillRow row(SkillPickStatDomain stat) {
    long games = stat.getGames() == null ? 0 : stat.getGames();
    long wins = stat.getWins() == null ? 0 : stat.getWins();
    return new SkillRow(label(stat), games, wins, games == 0 ? 0 : (double) wins / games);
  }

  private String label(SkillPickStatDomain stat) {
    return switch (stat.getKind()) {
      case HERO -> heroName(stat.getFirstId());
      case SKILL -> abilityName(stat.getFirstId());
      case HERO_SKILL -> heroName(stat.getFirstId()) + " with " + abilityName(stat.getSecondId());
      case SKILL_PAIR -> abilityName(stat.getFirstId()) + " + " + abilityName(stat.getSecondId());
    };
  }

  private String heroName(Long id) {
    return constants.getHeroConstantMap().values().stream()
        .filter(h -> id != null && String.valueOf(id).equals(h.getId()))
        .map(HeroConstant::getLocalized_name)
        .findFirst()
        .orElse("hero " + id);
  }

  private String abilityName(Long id) {
    return constants.getAbilityConstantMap().values().stream()
        .filter(a -> a.getId() != null && a.getId().equals(id))
        .map(AbilityIdsConstant::getName)
        .findFirst()
        .orElse("ability " + id);
  }
}
