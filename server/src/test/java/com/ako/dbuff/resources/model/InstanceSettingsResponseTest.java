package com.ako.dbuff.resources.model;

import com.ako.dbuff.dao.model.InstanceSettings;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InstanceSettingsResponseTest {

  @Test
  void itemCategories_unsetShowsAll() {
    assertThat(InstanceSettingsResponse.from(new InstanceSettings()).getItemCategories())
        .isEqualTo("all");
  }

  @Test
  void itemCategories_chosenAreShownAsStored() {
    InstanceSettings settings =
        InstanceSettings.builder().itemCategories("farming,sustain").build();

    assertThat(InstanceSettingsResponse.from(settings).getItemCategories())
        .isEqualTo("farming,sustain");
  }
}
