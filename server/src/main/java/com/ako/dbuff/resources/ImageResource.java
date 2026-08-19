package com.ako.dbuff.resources;

import com.ako.dbuff.resources.model.ExtractedPlayerNamesResponse;
import com.ako.dbuff.service.ImageProcessor;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST API for extracting player names from an uploaded scoreboard image using Google Cloud Vision
 * text detection (OCR).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/image")
public class ImageResource {

  private final ImageProcessor imageProcessor;

  /**
   * Accepts an image upload and returns the player names detected in it (expected up to 10).
   *
   * @param image the uploaded image file (PNG, JPEG, etc.)
   * @return the list of extracted player names
   * @throws IOException if the uploaded file cannot be read
   */
  @PostMapping(value = "/players", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ExtractedPlayerNamesResponse> extractPlayerNames(
      @RequestParam("image") MultipartFile image) throws IOException {

    log.info(
        "POST /api/v1/image/players - filename={}, size={} bytes",
        image.getOriginalFilename(),
        image.getSize());

    List<String> playerNames = imageProcessor.extractPlayerNames(image.getBytes());

    return ResponseEntity.ok(
        ExtractedPlayerNamesResponse.builder().playerNames(playerNames).build());
  }
}
