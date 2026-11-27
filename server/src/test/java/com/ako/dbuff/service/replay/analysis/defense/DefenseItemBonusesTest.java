package com.ako.dbuff.service.replay.analysis.defense;

import com.ako.dbuff.service.constant.data.ItemConstant;
import com.ako.dbuff.service.replay.analysis.defense.DefenseItemBonuses.Bonus;
import com.ako.dbuff.service.replay.analysis.item.ItemKits;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DefenseItemBonusesTest {

  private static Map<String, String> attr(String key, String value, String display) {
    Map<String, String> m = new HashMap<>();
    m.put("key", key);
    m.put("value", value);
    if (display != null) {
      m.put("display", display);
    }
    return m;
  }

  private static ItemConstant item(Map<String, String>... attribs) {
    return ItemConstant.builder().attrib(List.of(attribs)).build();
  }

  private final Map<String, ItemConstant> items =
      Map.of(
          "shivas_guard", item(attr("bonus_armor", "17", "+ {value} Armor")),
          "assault",
              item(
                  attr("bonus_armor", "10", "+ {value} Armor"),
                  attr("aura_positive_armor", "5", null)),
          "hood_of_defiance",
              item(
                  attr("bonus_magical_armor", "18", null),
                  attr("tooltip_resist", "18", "+ {value}% Magic Resistance")),
          "nether_shawl",
              item(
                  attr("armor_minus", "2", "- {value} Armor"),
                  attr("bonus_magic_resistance", "20", "+ {value}% Magic Resistance")),
          "enhanced",
              item(
                  attr("bonus_armor", "0 / 4 / 6 / 8", "+ {value} Armor"),
                  attr("x", "4 / 6", "+ {value}% Magic Resistance")),
          "double_mr",
              item(
                  attr("a", "20", "+ {value}% Magic Resistance"),
                  attr("b", "10", "+ {value}% Magic Resistance")));

  private final ItemKits kits = key -> Optional.ofNullable(items.get(key));
  private final DefenseItemBonuses bonuses = new DefenseItemBonuses(kits);

  @Test
  void armorFromDisplayedEntry() {
    assertThat(bonuses.of("item_shivas_guard")).isEqualTo(new Bonus(17, 0));
  }

  @Test
  void auraWithoutDisplayIsIgnored() {
    assertThat(bonuses.of("item_assault").armor()).isEqualTo(10);
  }

  @Test
  void magicResistFromDisplayedEntryOnly() {
    Bonus b = bonuses.of("item_hood_of_defiance");
    assertThat(b.armor()).isZero();
    assertThat(b.magicResist()).isCloseTo(0.18, within(1e-9));
  }

  @Test
  void minusArmorAndMagicResist() {
    Bonus b = bonuses.of("item_nether_shawl");
    assertThat(b.armor()).isEqualTo(-2);
    assertThat(b.magicResist()).isCloseTo(0.20, within(1e-9));
  }

  @Test
  void nonNumericValuesAreIgnored() {
    assertThat(bonuses.of("item_enhanced")).isEqualTo(Bonus.NONE);
  }

  @Test
  void severalResistancesCombineMultiplicatively() {
    assertThat(bonuses.of("double_mr").magicResist()).isCloseTo(0.28, within(1e-9));
  }

  @Test
  void prefixIsOptionalAndUnknownItemIsZero() {
    assertThat(bonuses.of("shivas_guard")).isEqualTo(bonuses.of("item_shivas_guard"));
    assertThat(bonuses.of("item_nothing")).isEqualTo(Bonus.NONE);
  }
}
