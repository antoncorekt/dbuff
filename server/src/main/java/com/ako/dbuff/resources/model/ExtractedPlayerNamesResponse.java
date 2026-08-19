package com.ako.dbuff.resources.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response model holding the player names extracted from an uploaded image via OCR. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedPlayerNamesResponse {

  /** Player names detected in the image (expected up to 10). */
  private List<String> playerNames;
}
