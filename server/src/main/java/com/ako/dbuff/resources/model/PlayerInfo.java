package com.ako.dbuff.resources.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Player information for API responses. Contains both ID and name for user-friendly display. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerInfo {

  /** Player's Dota 2 account ID */
  private Long id;

  /** Player's display name */
  private String name;
}
