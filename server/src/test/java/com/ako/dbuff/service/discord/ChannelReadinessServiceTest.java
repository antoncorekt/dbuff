package com.ako.dbuff.service.discord;

import java.util.List;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelReadinessServiceTest {

  private JDA jda;
  private TextChannel channel;
  private SelfMember self;
  private ChannelReadinessService service;

  @BeforeEach
  void setUp() {
    jda = Mockito.mock(JDA.class);
    channel = Mockito.mock(TextChannel.class);
    Guild guild = Mockito.mock(Guild.class);
    self = Mockito.mock(SelfMember.class);
    Mockito.when(channel.getGuild()).thenReturn(guild);
    Mockito.when(guild.getSelfMember()).thenReturn(self);
    Mockito.when(jda.getTextChannelById("channel-1")).thenReturn(channel);
    BotPermissions.REQUIRED.forEach(
        permission -> Mockito.when(self.hasPermission(channel, permission)).thenReturn(true));
    service = new ChannelReadinessService(jda);
  }

  @Test
  void channelWithEveryRequiredPermission_hasNoProblems() {
    assertThat(service.findProblems("channel-1")).isEmpty();
  }

  @Test
  void missingPermission_isNamedInTheProblem() {
    Mockito.when(self.hasPermission(channel, Permission.CREATE_PUBLIC_THREADS)).thenReturn(false);

    List<String> problems = service.findProblems("channel-1");

    assertThat(problems).hasSize(1);
    assertThat(problems.get(0)).contains("Create Public Threads");
  }

  @Test
  void unknownOrNonTextChannel_isReported() {
    assertThat(service.findProblems("forum-9")).hasSize(1);
    assertThat(service.findProblems("forum-9").get(0)).contains("not a text channel");
  }
}
