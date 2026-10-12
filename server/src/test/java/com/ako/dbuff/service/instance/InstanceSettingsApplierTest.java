package com.ako.dbuff.service.instance;

import com.ako.dbuff.dao.model.InstanceSettings;
import com.ako.dbuff.dao.model.ReportDetail;
import com.ako.dbuff.resources.model.UpdateInstanceRequest;
import com.ako.dbuff.service.constant.GameModeResolver;
import com.ako.dbuff.service.constant.GameModeSelection;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstanceSettingsApplierTest {

  private GameModeResolver resolver;
  private InstanceSettingsApplier applier;
  private InstanceSettings settings;

  @BeforeEach
  void setUp() {
    resolver = Mockito.mock(GameModeResolver.class);
    applier = new InstanceSettingsApplier(resolver);
    settings = new InstanceSettings();
  }

  private void apply(UpdateInstanceRequest.UpdateInstanceRequestBuilder request) {
    applier.apply(settings, request.build());
  }

  private static Set<String> set(String... values) {
    return new LinkedHashSet<>(java.util.List.of(values));
  }

  @Test
  void aRequestWithNoSettings_changesNothing() {
    apply(UpdateInstanceRequest.builder().name("Squad"));

    assertThat(settings).isEqualTo(new InstanceSettings());
  }

  @Test
  void aiSummary_canBeTurnedOffAndOn() {
    apply(UpdateInstanceRequest.builder().aiSummaryEnabled(false));
    assertThat(settings.isAiSummaryOn()).isFalse();

    apply(UpdateInstanceRequest.builder().aiSummaryEnabled(true));
    assertThat(settings.isAiSummaryOn()).isTrue();
  }

  @Test
  void detail_isCaseInsensitive() {
    apply(UpdateInstanceRequest.builder().reportDetail(" Minimal "));

    assertThat(settings.detail()).isEqualTo(ReportDetail.MINIMAL);
  }

  @Test
  void detail_unknownLevel_isRejectedWithTheValidChoices() {
    assertThatThrownBy(() -> apply(UpdateInstanceRequest.builder().reportDetail("loud")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("minimal, standard or full");
  }

  @ParameterizedTest
  @ValueSource(strings = {"01:00-09:00", "1:00 - 9:00", " 01:00-09:00 "})
  void quietHours_acceptsAWindow(String window) {
    apply(UpdateInstanceRequest.builder().quietHours(window));

    assertThat(settings.getQuietHoursEnabled()).isTrue();
    assertThat(settings.getQuietStart()).isEqualTo(LocalTime.of(1, 0));
    assertThat(settings.getQuietEnd()).isEqualTo(LocalTime.of(9, 0));
  }

  @Test
  void quietHours_windowMayWrapMidnight() {
    apply(UpdateInstanceRequest.builder().quietHours("23:30-06:15"));

    assertThat(settings.getQuietStart()).isEqualTo(LocalTime.of(23, 30));
    assertThat(settings.getQuietEnd()).isEqualTo(LocalTime.of(6, 15));
  }

  @Test
  void replayReport_isStoredAsChosen() {
    apply(UpdateInstanceRequest.builder().replayReportEnabled(false));
    assertThat(settings.getReplayReportEnabled()).isFalse();
    assertThat(settings.isReplayReportOn()).isFalse();

    apply(UpdateInstanceRequest.builder().replayReportEnabled(true));
    assertThat(settings.isReplayReportOn()).isTrue();
  }

  @Test
  void quietHours_offAndDefault() {
    apply(UpdateInstanceRequest.builder().quietHours("22:00-07:00"));

    apply(UpdateInstanceRequest.builder().quietHours("OFF"));
    assertThat(settings.getQuietHoursEnabled()).isFalse();
    assertThat(settings.getQuietStart()).isNull();

    apply(UpdateInstanceRequest.builder().quietHours("default"));
    assertThat(settings.getQuietHoursEnabled()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"nonsense", "25:00-07:00", "01:00-09:61", "09:00", "01:00-01:00", ""})
  void quietHours_unreadable_isRejected(String raw) {
    assertThatThrownBy(() -> apply(UpdateInstanceRequest.builder().quietHours(raw)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void quietHours_rejectedChange_leavesTheOldWindowInPlace() {
    apply(UpdateInstanceRequest.builder().quietHours("22:00-07:00"));

    assertThatThrownBy(() -> apply(UpdateInstanceRequest.builder().quietHours("25:00-07:00")))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(settings.getQuietStart()).isEqualTo(LocalTime.of(22, 0));
  }

  @Test
  void timezone_storesTheCanonicalId() {
    apply(UpdateInstanceRequest.builder().timezone("Europe/Kyiv"));

    assertThat(settings.getTimezone()).isEqualTo("Europe/Kyiv");
  }

  @Test
  void timezone_defaultClearsIt() {
    apply(UpdateInstanceRequest.builder().timezone("Europe/Kyiv"));
    apply(UpdateInstanceRequest.builder().timezone("default"));

    assertThat(settings.getTimezone()).isNull();
  }

  @Test
  void timezone_unknown_isRejected() {
    assertThatThrownBy(() -> apply(UpdateInstanceRequest.builder().timezone("Mars/Base")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Mars/Base");
  }

  @Test
  void gameModes_areStoredAsCanonicalNames() {
    Mockito.when(resolver.resolve(set("ability_draft", "all_pick")))
        .thenReturn(
            new GameModeSelection(
                set("game_mode_ability_draft", "game_mode_all_draft"),
                Set.of(18L, 22L),
                Set.of(),
                "Ability Draft, All Draft"));

    apply(UpdateInstanceRequest.builder().defaultGameModes(set("ability_draft", "all_pick")));

    assertThat(settings.getDefaultGameModes())
        .isEqualTo("game_mode_ability_draft,game_mode_all_draft");
    assertThat(settings.defaultGameModeTokens())
        .containsExactly("game_mode_ability_draft", "game_mode_all_draft");
  }

  @Test
  void gameModes_all_isStoredAsAll() {
    Mockito.when(resolver.resolve(set("all"))).thenReturn(GameModeSelection.allModes());

    apply(UpdateInstanceRequest.builder().defaultGameModes(set("all")));

    assertThat(settings.getDefaultGameModes()).isEqualTo("all");
  }

  @Test
  void gameModes_unknown_isRejectedWithASuggestion() {
    Mockito.when(resolver.resolve(set("ability_draf")))
        .thenReturn(new GameModeSelection(Set.of(), Set.of(), Set.of("ability_draf"), "All modes"));
    Mockito.when(resolver.suggest("ability_draf")).thenReturn(Optional.of("Ability Draft"));

    assertThatThrownBy(
            () -> apply(UpdateInstanceRequest.builder().defaultGameModes(set("ability_draf"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ability_draf")
        .hasMessageContaining("Ability Draft");
    assertThat(settings.getDefaultGameModes()).isNull();
  }

  @Test
  void gameModes_defaultClearsTheChoice() {
    settings.setDefaultGameModes("game_mode_ability_draft");

    apply(UpdateInstanceRequest.builder().defaultGameModes(set("default")));

    assertThat(settings.getDefaultGameModes()).isNull();
    assertThat(settings.defaultGameModeTokens()).isEmpty();
  }

  @Test
  void gameModes_defaultAmongOthers_isRejectedRatherThanDroppingThem() {
    assertThatThrownBy(
            () ->
                apply(
                    UpdateInstanceRequest.builder()
                        .defaultGameModes(set("ability_draft", "default"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("default");
  }

  @Test
  void gameModes_emptyOrBlankSet_isRejectedNotStoredAsAll() {
    settings.setDefaultGameModes("game_mode_ability_draft");

    assertThatThrownBy(() -> apply(UpdateInstanceRequest.builder().defaultGameModes(Set.of())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> apply(UpdateInstanceRequest.builder().defaultGameModes(set(" ", ""))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(settings.getDefaultGameModes()).isEqualTo("game_mode_ability_draft");
  }

  @Test
  void seed_usesTheRegisteredModes() {
    Mockito.when(resolver.resolve(set("Ability Draft")))
        .thenReturn(
            new GameModeSelection(
                set("game_mode_ability_draft"), Set.of(18L), Set.of(), "Ability Draft"));

    applier.seedDefaultModes(settings, set("Ability Draft"));

    assertThat(settings.getDefaultGameModes()).isEqualTo("game_mode_ability_draft");
  }

  @Test
  void seed_withNoModes_leavesTheDefaultAlone() {
    settings.setDefaultGameModes("all");

    applier.seedDefaultModes(settings, null);
    applier.seedDefaultModes(settings, Set.of());

    assertThat(settings.getDefaultGameModes()).isEqualTo("all");
  }

  @Test
  void seed_withUnreadableModes_doesNotFailRegistration() {
    Mockito.when(resolver.resolve(set("???")))
        .thenReturn(new GameModeSelection(Set.of(), Set.of(), Set.of("???"), "All modes"));

    applier.seedDefaultModes(settings, set("???"));

    assertThat(settings.getDefaultGameModes()).isNull();
  }
}
