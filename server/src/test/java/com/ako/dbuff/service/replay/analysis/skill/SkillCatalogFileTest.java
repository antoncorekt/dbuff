package com.ako.dbuff.service.replay.analysis.skill;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillCatalogFileTest {

  private static SkillCatalogFile of(String yaml) {
    return new SkillCatalogFile(
        () -> new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "test.yaml");
  }

  @Test
  void entryParametersLieOverTheDefaults() {
    SkillCatalogFile file =
        of(
            """
            parameters: { a: 1, b: 2 }
            entries:
              - key: k
                abilities: [x_skill]
                aspects: [promise]
                parameters: { b: 5 }
            """);

    SkillCatalogEntry entry = file.entries().getFirst();

    assertThat(entry.abilities()).containsExactly("x_skill");
    assertThat(entry.parameter("a", 0)).isEqualTo(1);
    assertThat(entry.parameter("b", 0)).isEqualTo(5);
    assertThat(file.defaults()).containsEntry("b", 2.0);
  }

  @Test
  void theShippedFileLoads() {
    SkillCatalogFile file = new SkillCatalogFile("replay/skills.yaml");

    assertThat(file.entries())
        .extracting(SkillCatalogEntry::key)
        .containsExactly("false_promise", "meat_hook", "ice_shards");
    assertThat(file.entries().getFirst().abilities()).containsExactly("oracle_false_promise");
  }

  @Test
  void anUnknownFieldFails() {
    assertThatThrownBy(
            () ->
                of("""
                        entries:
                          - key: k
                            abilities: [x]
                            aspects: [promise]
                            colour: red
                        """)
                    .entries())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("test.yaml");
  }

  @Test
  void everyProblemIsNamed() {
    assertThatThrownBy(
            () ->
                of("""
                        entries:
                          - key: k
                            abilities: [x]
                            aspects: [promise]
                            parameters: { n: -1 }
                          - key: k
                            abilities: [x]
                            aspects: []
                        """)
                    .entries())
        .hasMessageContaining("'k' is listed twice")
        .hasMessageContaining("ability 'x' is listed by more than one entry")
        .hasMessageContaining("'k' lists no aspects")
        .hasMessageContaining("'k' parameters 'n' must be a number of 0 or more");
  }

  @Test
  void aFileWithoutEntriesFails() {
    assertThatThrownBy(() -> of("parameters: { a: 1 }\n").entries())
        .hasMessageContaining("has no entries");
  }
}
