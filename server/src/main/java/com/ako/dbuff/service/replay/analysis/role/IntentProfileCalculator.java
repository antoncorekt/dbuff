package com.ako.dbuff.service.replay.analysis.role;

import com.ako.dbuff.service.constant.data.AbilityAttribute;
import com.ako.dbuff.service.constant.data.AbilityConstant;
import com.ako.dbuff.service.constant.data.HeroConstant;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.BiPredicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reads a function profile off a hero and the skills he holds, from OpenDota's constants alone. The
 * rules are guesses of what a skill is for (see {@link RoleProfileProperties.Intent}); the
 * constants have no disable, blink or ultimate flag, so those come from attribute keys and from the
 * slot the skill was drafted into. The lane and the vision duty cannot be told from a kit and stay
 * unknown.
 */
@Component
@RequiredArgsConstructor
public class IntentProfileCalculator {
  private static final String FRIENDLY = "Friendly";
  private static final String ENEMY = "Enemy";
  private static final String UNIT_TARGET = "Unit Target";
  private static final String AREA = "AOE";
  private static final String NO_TARGET = "No Target";
  private static final String BKB_PIERCE = "Yes";

  private final RoleProfileProperties properties;

  /** A skill of the kit; the ultimate counts more than a regular one. */
  public record HeldAbility(AbilityConstant constant, boolean ultimate) {}

  /**
   * @param hero null when the hero is not in the constants
   * @param abilities the skills in the constants; the ones not found are left out by the caller
   */
  public FunctionProfile profile(HeroConstant hero, List<HeldAbility> abilities) {
    if (hero == null && abilities.isEmpty()) {
      return FunctionProfile.UNKNOWN;
    }
    RoleProfileProperties.Intent rules = properties.getIntent();
    Sums sums = new Sums();
    for (HeldAbility held : abilities) {
      sums.add(held, rules);
    }
    return new FunctionProfile(
        null,
        farmPriority(hero, sums, rules),
        hero == null ? null : tag(hero, rules.getJunglerTagName()) * rules.getJunglerTag(),
        roaming(hero, sums, rules),
        null,
        clamp(
            sums.areaControl / rules.getDisableFull()
                + sums.gapClosers * rules.getGapCloserInitiation()),
        clamp(sums.disable / rules.getDisableFull()),
        clamp(sums.save / rules.getSaveFull()),
        damage(hero, sums, rules),
        durability(hero, sums, rules));
  }

  private static Double farmPriority(HeroConstant hero, Sums sums, RoleProfileProperties.Intent r) {
    double body = 0;
    if (hero != null) {
      body += "agi".equals(hero.getPrimary_attr()) ? r.getAgilityBody() : 0;
      body += "Ranged".equals(hero.getAttack_type()) ? r.getRangedBody() : 0;
      body += tag(hero, r.getCarryTagName()) * r.getCarryTag();
    }
    return clamp(body + sums.scaling * r.getScalingSkill() + sums.farmSkills * r.getFarmSkill());
  }

  private static Double roaming(HeroConstant hero, Sums sums, RoleProfileProperties.Intent r) {
    double skills =
        clamp((sums.mobility + sums.cheap * r.getCheapSkillRoaming()) / r.getRoamingFull());
    double speed =
        hero == null || hero.getMove_speed() == null
            ? 0
            : clamp((hero.getMove_speed() - r.getMoveSpeedBase()) / r.getMoveSpeedFull());
    return clamp(skills * (1 - r.getMoveSpeedShare()) + speed * r.getMoveSpeedShare());
  }

  private static Double damage(HeroConstant hero, Sums sums, RoleProfileProperties.Intent r) {
    double attack = 0;
    if (hero != null && hero.getBase_attack_min() != null && hero.getBase_attack_max() != null) {
      attack = (hero.getBase_attack_min() + hero.getBase_attack_max()) / 2;
    }
    return clamp(
        clamp(sums.damage / r.getDamageFull()) * (1 - r.getAttackDamageShare())
            + clamp(attack / r.getAttackDamageFull()) * r.getAttackDamageShare());
  }

  private static Double durability(HeroConstant hero, Sums sums, RoleProfileProperties.Intent r) {
    double body = 0;
    if (hero != null) {
      double strength =
          value(hero.getBase_str()) + value(hero.getStr_gain()) * (r.getBodyLevel() - 1);
      body =
          clamp(strength / r.getStrengthFull()) * r.getStrengthShare()
              + clamp(value(hero.getBase_armor()) / r.getArmorFull()) * r.getArmorShare()
              + tag(hero, r.getDurableTagName()) * r.getDurableTag();
    }
    return clamp(
        body + clamp(sums.durability / r.getDurabilitySkillFull()) * r.getDurabilitySkillShare());
  }

  private static double tag(HeroConstant hero, String name) {
    return hero.getRoles() != null && hero.getRoles().contains(name) ? 1 : 0;
  }

  private static double value(Double number) {
    return number == null ? 0 : number;
  }

  private static double clamp(double value) {
    return Math.max(0, Math.min(1, value));
  }

  /** The skills of a kit added up, each already counted with the weight of its slot. */
  private static final class Sums {
    double disable;
    double areaControl;
    double gapClosers;
    double mobility;
    double damage;
    double save;
    double scaling;
    double farmSkills;
    double durability;
    double cheap;

    void add(HeldAbility held, RoleProfileProperties.Intent r) {
      AbilityConstant a = held.constant();
      double weight = held.ultimate() ? r.getUltimateWeight() : r.getSkillWeight();
      double cooldown = Math.max(cooldown(a).orElse(0), r.getCooldownFloorSeconds());
      double control = control(a, r);
      double hit = Math.max(maxOf(a.getDmg()), number(a, r.getDamageKeys(), String::equals));
      boolean area = has(a.getBehavior(), AREA) || has(a.getBehavior(), NO_TARGET);
      if (control > 0) {
        double bkb = has(a.getBkbpierce(), BKB_PIERCE) ? r.getBkbPierceFactor() : 1;
        double value = weight * control * bkb / cooldown;
        disable += value;
        areaControl += area ? value * r.getInitiationDisableShare() : 0;
      }
      if (hasKey(a, r.getGapCloserKeys())) {
        gapClosers += weight;
        mobility += weight;
      }
      if (hasKey(a, r.getMobilityKeys())) {
        mobility += weight;
      }
      if (hit > 0) {
        damage += weight * hit / cooldown;
        farmSkills +=
            has(a.getBehavior(), AREA) && cooldown <= r.getFarmSkillMaxCooldown() ? weight : 0;
      }
      if ((control > 0 || hit > 0)
          && firstManaCost(a) <= r.getCheapManaCost()
          && cooldown <= r.getCheapMaxCooldown()) {
        cheap += r.getSkillWeight();
      }
      if (has(a.getTarget_team(), FRIENDLY) && hasKey(a, r.getSaveKeys())) {
        save += weight;
      }
      scaling += hasKey(a, r.getScalingKeys()) ? weight : 0;
      durability += hasKey(a, r.getDurabilityKeys()) ? weight : 0;
    }

    private static OptionalDouble cooldown(AbilityConstant a) {
      return a.getCd() == null
          ? OptionalDouble.empty()
          : a.getCd().stream()
              .filter(Objects::nonNull)
              .mapToDouble(Double::doubleValue)
              .filter(c -> c > 0)
              .min();
    }

    private static double firstManaCost(AbilityConstant a) {
      return a.getMc() == null || a.getMc().isEmpty() || a.getMc().getFirst() == null
          ? Double.MAX_VALUE
          : a.getMc().getFirst();
    }

    private static double maxOf(List<Double> values) {
      return values == null
          ? 0
          : values.stream()
              .filter(Objects::nonNull)
              .mapToDouble(Double::doubleValue)
              .max()
              .orElse(0);
    }

    private static boolean has(List<String> values, String wanted) {
      return values != null && values.contains(wanted);
    }

    private static boolean hasKey(AbilityConstant a, List<String> keys) {
      return a.getAttrib() != null
          && a.getAttrib().stream().anyMatch(t -> keys.contains(t.getKey()) && positive(t));
    }

    /** Seconds of control: by key ending, and by plain duration on a skill aimed at one enemy. */
    private static double control(AbilityConstant a, RoleProfileProperties.Intent r) {
      double seconds = number(a, r.getDisableKeySuffixes(), String::endsWith);
      if (has(a.getTarget_team(), ENEMY) && has(a.getBehavior(), UNIT_TARGET)) {
        double plain = number(a, r.getEnemyUnitDisableKeys(), String::equals);
        seconds = plain > r.getEnemyUnitDisableMaxSeconds() ? seconds : Math.max(seconds, plain);
      }
      return seconds;
    }

    /** The largest value of the attributes with a matching key; 0 when there is none. */
    private static double number(
        AbilityConstant a, List<String> keys, BiPredicate<String, String> matches) {
      if (a.getAttrib() == null) {
        return 0;
      }
      return a.getAttrib().stream()
          .filter(t -> keys.stream().anyMatch(k -> matches.test(t.getKey(), k)))
          .flatMap(
              t ->
                  t.getValue() == null
                      ? java.util.stream.Stream.<String>empty()
                      : t.getValue().stream())
          .mapToDouble(Sums::parse)
          .max()
          .orElse(0);
    }

    private static boolean positive(AbilityAttribute attribute) {
      return attribute.getValue() != null
          && attribute.getValue().stream().anyMatch(v -> parse(v) > 0);
    }

    private static double parse(String text) {
      try {
        return Math.abs(Double.parseDouble(text.replace("%", "").trim()));
      } catch (NumberFormatException e) {
        return 0;
      }
    }
  }
}
