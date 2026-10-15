package com.ako.dbuff.service.replay.analysis.buildfit;

import com.ako.dbuff.service.replay.analysis.role.FunctionValues;
import com.ako.dbuff.service.replay.analysis.role.RoleFitter;
import com.ako.dbuff.service.replay.analysis.role.RoleFunction;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The checked-in file {@code replay/role-needs.json}: per role number 1 to 5, the least value of
 * each function a good build holds. The file is read and checked when the application starts, so an
 * unknown role or function, a value outside (0, 1], an empty entry or a duplicate stops it with the
 * entry named.
 *
 * <pre>{"1": {"DAMAGE": 0.4, "FARM_PRIORITY": 0.5}}</pre>
 */
@Component
public class RoleNeedsFile implements RoleNeeds {
  private static final String LABEL = "Role needs file";

  private final Map<Integer, Map<RoleFunction, Double>> needs = new HashMap<>();

  @Autowired
  public RoleNeedsFile(
      @Value("${replay.analysis.role-needs.resource:replay/role-needs.json}") String resource) {
    this(open(resource), resource);
  }

  RoleNeedsFile(InputStream in, String source) {
    read(in, source)
        .forEach(
            (key, values) -> {
              needs.put(role(source, key), FunctionValues.parse(LABEL, source, key, values));
            });
  }

  @Override
  public Map<RoleFunction, Double> forRole(int role) {
    return needs.getOrDefault(role, Map.of());
  }

  private static int role(String source, String key) {
    try {
      int role = Integer.parseInt(key);
      if (role >= 1 && role <= RoleFitter.ROLES) {
        return role;
      }
    } catch (NumberFormatException e) {
      // reported below with the entry named
    }
    throw FunctionValues.invalid(LABEL, source, "'" + key + "' is not a role from 1 to 5");
  }

  private static Map<String, Map<String, Double>> read(InputStream in, String source) {
    try (in) {
      Map<String, Map<String, Double>> root =
          new ObjectMapper()
              .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
              .readValue(in, new TypeReference<>() {});
      if (root == null) {
        throw FunctionValues.invalid(LABEL, source, "is empty");
      }
      return root;
    } catch (IOException e) {
      throw new IllegalStateException(LABEL + " " + source + ": " + e.getMessage(), e);
    }
  }

  private static InputStream open(String resource) {
    try {
      return new ClassPathResource(resource).getInputStream();
    } catch (IOException e) {
      throw new IllegalStateException(LABEL + " " + resource + ": cannot be opened", e);
    }
  }
}
