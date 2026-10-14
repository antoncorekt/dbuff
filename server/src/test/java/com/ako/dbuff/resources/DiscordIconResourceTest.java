package com.ako.dbuff.resources;

import com.ako.dbuff.service.discord.icon.IconKind;
import com.ako.dbuff.service.discord.icon.IconProperties;
import com.ako.dbuff.service.discord.icon.IconSyncService;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DiscordIconResourceTest {
  private final IconSyncService sync = mock(IconSyncService.class);
  private final IconProperties properties = new IconProperties();
  private final DiscordIconResource resource = new DiscordIconResource(sync, properties);

  @BeforeEach
  void setUp() {
    properties.setEnabled(true);
  }

  @Test
  void switchedOff_isConflictAndStartsNothing() {
    properties.setEnabled(false);

    assertThat(resource.sync("ALL", false).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    verifyNoInteractions(sync);
  }

  @Test
  void unknownKind_isBadRequest() {
    assertThat(resource.sync("BOOTS", false).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(sync);
  }

  @Test
  void all_startsEveryKindWithSeenAbilitiesOnly() {
    when(sync.start(any(), anyBoolean())).thenReturn(true);

    assertThat(resource.sync("all", true).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    verify(sync).start(EnumSet.allOf(IconKind.class), false);
  }

  @Test
  void abilityWithAll_startsEveryAbility() {
    when(sync.start(any(), anyBoolean())).thenReturn(true);

    resource.sync("ABILITY", true);

    verify(sync).start(Set.of(IconKind.ABILITY), true);
  }

  @Test
  void runningSync_isConflict() {
    when(sync.start(any(), anyBoolean())).thenReturn(false);

    assertThat(resource.sync("HERO", false).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }
}
