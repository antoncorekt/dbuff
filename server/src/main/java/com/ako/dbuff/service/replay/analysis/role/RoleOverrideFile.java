package com.ako.dbuff.service.replay.analysis.role;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The checked-in override file ({@code replay/role-overrides.json}): per skill and per hero, the
 * function values the constants cannot tell, for example a taunt that is a disable and an
 * initiation. A value is for a regular skill; an ultimate counts more, as in the rules. The file is
 * read and checked when the application starts, so a typo stops it with the entry named.
 *
 * <pre>
 * {"abilities": {"axe_berserkers_call": {"DISABLE": 0.5}}, "heroes": {"2": {"DURABILITY": 0.2}}}
 * </pre>
 */
@Component
public class RoleOverrideFile implements RoleOverrides {
  private static final String LABEL = "Role override file";
  private static final Pattern ABILITY_NAME = Pattern.compile("[a-z][a-z0-9]*(_[a-z0-9]+)+");
  private static final Pattern HERO_ID = Pattern.compile("[1-9][0-9]*");

  private record Content(
      Map<String, Map<String, Double>> abilities, Map<String, Map<String, Double>> heroes) {}

  private final Map<String, Map<RoleFunction, Double>> abilities;
  private final Map<Long, Map<RoleFunction, Double>> heroes;

  @Autowired
  public RoleOverrideFile(
      @Value("${replay.analysis.role-overrides.resource:replay/role-overrides.json}")
          String resource) {
    this(open(resource), resource);
  }

  RoleOverrideFile(InputStream in, String source) {
    Content content = read(in, source);
    abilities = new HashMap<>();
    content
        .abilities()
        .forEach(
            (name, values) -> {
              if (!ABILITY_NAME.matcher(name).matches()) {
                throw invalid(
                    source, "ability name '" + name + "' is not like axe_berserkers_call");
              }
              abilities.put(name, functions(source, name, values));
            });
    heroes = new HashMap<>();
    content
        .heroes()
        .forEach(
            (id, values) -> {
              if (!HERO_ID.matcher(id).matches()) {
                throw invalid(source, "hero id '" + id + "' is not a number");
              }
              heroes.put(Long.parseLong(id), functions(source, id, values));
            });
  }

  @Override
  public Map<RoleFunction, Double> forAbility(String name) {
    return abilities.getOrDefault(name, Map.of());
  }

  @Override
  public Map<RoleFunction, Double> forHero(long heroId) {
    return heroes.getOrDefault(heroId, Map.of());
  }

  private static Map<RoleFunction, Double> functions(
      String source, String entry, Map<String, Double> values) {
    return FunctionValues.parse(LABEL, source, entry, values);
  }

  private static Content read(InputStream in, String source) {
    try (in) {
      ObjectMapper mapper =
          new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
      Map<String, Map<String, Map<String, Double>>> root =
          mapper.readValue(in, new TypeReference<>() {});
      if (root == null) {
        throw invalid(source, "is empty");
      }
      for (String section : root.keySet()) {
        if (!section.equals("abilities") && !section.equals("heroes")) {
          throw invalid(source, "unknown section '" + section + "'");
        }
      }
      for (Map.Entry<String, Map<String, Map<String, Double>>> section : root.entrySet()) {
        if (section.getValue() == null) {
          throw invalid(source, "section '" + section.getKey() + "' is null");
        }
      }
      return new Content(
          root.getOrDefault("abilities", Map.of()), root.getOrDefault("heroes", Map.of()));
    } catch (IOException e) {
      throw new IllegalStateException(
          "Role override file " + source + ": cannot be read: " + e.getMessage(), e);
    }
  }

  private static InputStream open(String resource) {
    try {
      return new ClassPathResource(resource).getInputStream();
    } catch (IOException e) {
      throw new IllegalStateException("Role override file " + resource + ": cannot be opened", e);
    }
  }

  private static IllegalStateException invalid(String source, String problem) {
    return FunctionValues.invalid(LABEL, source, problem);
  }
}
