package com.ako.dbuff.dao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PlayerDomain {
  @Column private Long id;

  @Id private String name;

  /**
   * Discord user ID (snowflake) linked to this player, or null when unlinked.
   *
   * <p>Stored as a String because Discord snowflakes are 64-bit unsigned and JDA hands them out as
   * strings throughout. Deliberately the snowflake rather than a nickname: nicknames are per-guild
   * and change freely, so a nickname-based link breaks silently the moment someone renames.
   */
  @Column(name = "discord_user_id")
  private String discordUserId;
}
