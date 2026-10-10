package com.ako.dbuff.service.discord;

import com.ako.dbuff.resources.model.DbufInstanceConfigResponse;
import com.ako.dbuff.service.discord.command.adapter.GuildCommandRegistrar;
import com.ako.dbuff.service.instance.DbufInstanceConfigService;
import java.util.List;
import java.util.Set;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class GuildLifecycleListenerTest {

  private GuildCommandRegistrar registrar;
  private DbufInstanceConfigService instanceConfigService;
  private GuildLifecycleListener listener;
  private Guild guild;
  private SelfMember selfMember;

  @BeforeEach
  void setUp() {
    registrar = Mockito.mock(GuildCommandRegistrar.class);
    instanceConfigService = Mockito.mock(DbufInstanceConfigService.class);
    listener = new GuildLifecycleListener(registrar, instanceConfigService);
    guild = Mockito.mock(Guild.class);
    Mockito.when(guild.getId()).thenReturn("guild-1");
    selfMember = Mockito.mock(SelfMember.class);
    Mockito.when(guild.getSelfMember()).thenReturn(selfMember);
  }

  private static DbufInstanceConfigResponse instance(String id, String guildId, String channelId) {
    return DbufInstanceConfigResponse.builder()
        .id(id)
        .discordGuildId(guildId)
        .discordChannelId(channelId)
        .active(true)
        .build();
  }

  private JDA readyJda(Set<String> unavailable, Guild... joined) {
    JDA jda = Mockito.mock(JDA.class);
    SelfUser self = Mockito.mock(SelfUser.class);
    Mockito.when(self.getId()).thenReturn("bot-1");
    Mockito.when(jda.getSelfUser()).thenReturn(self);
    Mockito.when(jda.getGuilds()).thenReturn(List.of(joined));
    Mockito.when(jda.getUnavailableGuilds()).thenReturn(unavailable);
    return jda;
  }

  private ReadyEvent ready(JDA jda) {
    ReadyEvent event = Mockito.mock(ReadyEvent.class);
    Mockito.when(event.getJDA()).thenReturn(jda);
    return event;
  }

  @Test
  void joiningAGuild_registersCommandsAndWelcomesInTheSystemChannel() {
    TextChannel channel = Mockito.mock(TextChannel.class);
    MessageCreateAction action = Mockito.mock(MessageCreateAction.class);
    Mockito.when(guild.getSystemChannel()).thenReturn(channel);
    Mockito.when(channel.canTalk()).thenReturn(true);
    Mockito.when(selfMember.hasPermission(channel, Permission.MESSAGE_EMBED_LINKS))
        .thenReturn(true);
    Mockito.when(
            channel.sendMessageEmbeds(Mockito.<net.dv8tion.jda.api.entities.MessageEmbed>any()))
        .thenReturn(action);
    GuildJoinEvent event = Mockito.mock(GuildJoinEvent.class);
    Mockito.when(event.getGuild()).thenReturn(guild);

    listener.onGuildJoin(event);

    Mockito.verify(registrar).register(guild);
    Mockito.verify(action).queue();
  }

  @Test
  void joiningAGuildWithNoWritableChannel_stillRegistersCommands() {
    Mockito.when(guild.getSystemChannel()).thenReturn(null);
    Mockito.when(guild.getTextChannels()).thenReturn(java.util.List.of());
    GuildJoinEvent event = Mockito.mock(GuildJoinEvent.class);
    Mockito.when(event.getGuild()).thenReturn(guild);

    listener.onGuildJoin(event);

    Mockito.verify(registrar).register(guild);
  }

  @Test
  void leavingAGuild_deactivatesItsInstances() {
    GuildLeaveEvent event = Mockito.mock(GuildLeaveEvent.class);
    Mockito.when(event.getGuild()).thenReturn(guild);

    listener.onGuildLeave(event);

    Mockito.verify(instanceConfigService).deactivateAllForGuild("guild-1");
  }

  @Test
  void welcome_withoutEmbedPermission_fallsBackToPlainText() {
    TextChannel channel = Mockito.mock(TextChannel.class);
    MessageCreateAction action = Mockito.mock(MessageCreateAction.class);
    Mockito.when(guild.getSystemChannel()).thenReturn(channel);
    Mockito.when(channel.canTalk()).thenReturn(true);
    Mockito.when(selfMember.hasPermission(channel, Permission.MESSAGE_EMBED_LINKS))
        .thenReturn(false);
    Mockito.when(channel.sendMessage(Mockito.anyString())).thenReturn(action);
    GuildJoinEvent event = Mockito.mock(GuildJoinEvent.class);
    Mockito.when(event.getGuild()).thenReturn(guild);

    listener.onGuildJoin(event);

    Mockito.verify(channel).sendMessage(Mockito.contains("/dbuff register"));
    Mockito.verify(action).queue();
  }

  @Test
  void ready_deactivatesInstancesOfGuildsTheBotLeftWhileOffline() {
    Mockito.when(instanceConfigService.getAllActive())
        .thenReturn(List.of(instance("kept", "guild-1", "c1"), instance("gone", "guild-2", "c2")));

    listener.onReady(ready(readyJda(Set.of(), guild)));

    Mockito.verify(instanceConfigService).deactivate("gone");
    Mockito.verify(instanceConfigService, Mockito.never()).deactivate("kept");
  }

  @Test
  void ready_treatsUnavailableGuildsAsJoined() {
    Mockito.when(instanceConfigService.getAllActive())
        .thenReturn(List.of(instance("outage", "guild-2", "c2")));

    listener.onReady(ready(readyJda(Set.of("guild-2"), guild)));

    Mockito.verify(instanceConfigService, Mockito.never()).deactivate(Mockito.any());
  }

  @Test
  void ready_backfillsMissingGuildIdFromTheChannel() {
    TextChannel channel = Mockito.mock(TextChannel.class);
    Mockito.when(channel.getGuild()).thenReturn(guild);
    JDA jda = readyJda(Set.of(), guild);
    Mockito.when(jda.getTextChannelById("c1")).thenReturn(channel);
    Mockito.when(instanceConfigService.getAllActive())
        .thenReturn(List.of(instance("legacy", null, "c1")));

    listener.onReady(ready(jda));

    var captor =
        org.mockito.ArgumentCaptor.forClass(
            com.ako.dbuff.resources.model.UpdateInstanceRequest.class);
    Mockito.verify(instanceConfigService).update(Mockito.eq("legacy"), captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().getDiscordGuildId())
        .isEqualTo("guild-1");
    Mockito.verify(instanceConfigService, Mockito.never()).deactivate(Mockito.any());
  }

  @Test
  void ready_leavesAnInstanceAloneWhenItsChannelCannotBeFound() {
    Mockito.when(instanceConfigService.getAllActive())
        .thenReturn(List.of(instance("legacy", null, "c1")));

    listener.onReady(ready(readyJda(Set.of(), guild)));

    Mockito.verify(instanceConfigService, Mockito.never()).update(Mockito.any(), Mockito.any());
    Mockito.verify(instanceConfigService, Mockito.never()).deactivate(Mockito.any());
  }
}
