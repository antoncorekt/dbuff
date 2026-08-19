package com.ako.dbuff.service;

import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.EntityAnnotation;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Image;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.Vertex;
import com.google.protobuf.ByteString;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Extracts player names from an uploaded image using Google Cloud Vision text detection (OCR).
 *
 * <p>The Vision API rejects requests whose (base64-encoded) payload is too large, so images at or
 * above {@link #MAX_IMAGE_SIZE_BYTES} are transparently re-encoded/scaled down before being sent.
 */
@Slf4j
@Service
public class ImageProcessor {

  /** Google Vision has a request size limit; keep the raw image comfortably under it. */
  static final int MAX_IMAGE_SIZE_BYTES = 4 * 1024 * 1024;

  /** Expected number of player names on a Dota 2 scoreboard (5 Radiant + 5 Dire). */
  private static final int EXPECTED_PLAYER_COUNT = 10;

  /** Lowest JPEG quality we will drop to while trying to fit under the size limit. */
  private static final float MIN_JPEG_QUALITY = 0.3f;

  /**
   * Labels shown above an unpicked Ability Draft slot ("ГЕРОЯ НЕТ" in Russian, "NO HERO" in
   * English), normalized (uppercased, non-alphanumeric stripped). Each such label sits directly
   * above that slot's player name, so it anchors name extraction.
   */
  private static final Set<String> HERO_SLOT_LABELS = Set.of("ГЕРОЯНЕТ", "NOHERO");

  /** Max vertical distance (px) between word centers considered part of the same text line. */
  private static final int LINE_Y_TOLERANCE = 12;

  /** Max vertical gap (px) between a slot label and the player name directly beneath it. */
  private static final int MAX_NAME_GAP_BELOW_LABEL = 60;

  /** Emitted for a slot whose label is detected but whose name OCR cannot read (e.g. "///"). */
  private static final String UNKNOWN_PLAYER = "UNKNOWN";

  private final ImageAnnotatorClient imageAnnotatorClient;

  /**
   * @param imageAnnotatorClient the Vision client, empty when no usable Google credentials were
   *     found — see {@code VisionConfig#imageAnnotatorClient()}. Taking this as an {@link Optional}
   *     keeps a missing credential from failing the application context, so only OCR degrades.
   */
  public ImageProcessor(Optional<ImageAnnotatorClient> imageAnnotatorClient) {
    this.imageAnnotatorClient = imageAnnotatorClient.orElse(null);
  }

  /**
   * Detects text in the given image and extracts up to {@value #EXPECTED_PLAYER_COUNT} player-name
   * candidates.
   *
   * @param imageBytes the raw image bytes (PNG, JPEG, etc.)
   * @return the list of extracted player names (may be fewer than expected if OCR is incomplete)
   * @throws IllegalStateException if no Vision client could be created at startup
   */
  public List<String> extractPlayerNames(byte[] imageBytes) {
    if (imageAnnotatorClient == null) {
      throw new IllegalStateException(
          "OCR is unavailable: no Google Vision client was created. Set "
              + "google.vision.credentials-path (or GOOGLE_APPLICATION_CREDENTIALS) and restart.");
    }
    if (imageBytes == null || imageBytes.length == 0) {
      throw new IllegalArgumentException("Image is empty");
    }

    byte[] payload = compressIfNeeded(imageBytes);

    Image image = Image.newBuilder().setContent(ByteString.copyFrom(payload)).build();
    Feature feature = Feature.newBuilder().setType(Feature.Type.DOCUMENT_TEXT_DETECTION).build();
    AnnotateImageRequest request =
        AnnotateImageRequest.newBuilder().addFeatures(feature).setImage(image).build();

    BatchAnnotateImagesResponse batchResponse =
        imageAnnotatorClient.batchAnnotateImages(List.of(request));
    AnnotateImageResponse response = batchResponse.getResponses(0);

    if (response.hasError()) {
      throw new IllegalStateException(
          "Google Vision returned an error: " + response.getError().getMessage());
    }

    if (log.isDebugEnabled()) {
      log.debug("Vision detected raw text:\n{}", response.getFullTextAnnotation().getText());
    }

    List<String> names = parsePlayerNames(response);
    log.info("Extracted {} player names: {}", names.size(), names);
    return names;
  }

  /**
   * Extracts player names from the Vision word-level annotations using the Ability Draft layout.
   *
   * <p>The flat {@code fullText} interleaves both player columns with unrelated centre-screen UI
   * text (draft board, chat, timers), so it cannot be split by lines. Instead this uses each word's
   * bounding box: names live in two vertical columns (far left and far right), and every player
   * name sits directly beneath a "ГЕРОЯ НЕТ" / "NO HERO" slot label. We therefore group words into
   * lines per column and, for each slot-label line, take the line immediately below it as the name.
   *
   * <p>NOTE: this is tuned to the Dota 2 Ability Draft pre-pick screen (where every slot shows the
   * "no hero" label). If a client language other than Russian/English is used, add its label to
   * {@link #HERO_SLOT_LABELS}. Names rendered as pure symbols (e.g. "///") may not be detected by
   * OCR; such slots are emitted as {@value #UNKNOWN_PLAYER} so the result still maps 1:1 to the ten
   * on-screen slots.
   *
   * @param response the Vision response containing text annotations with bounding boxes
   * @return up to {@value #EXPECTED_PLAYER_COUNT} player names, left column first then right
   */
  private List<String> parsePlayerNames(AnnotateImageResponse response) {
    List<EntityAnnotation> annotations = response.getTextAnnotationsList();
    if (annotations.size() <= 1) {
      return List.of();
    }

    // Index 0 is the whole-image text block; indexes 1..N are individual words with boxes.
    List<Word> words = new ArrayList<>();
    int maxX = 0;
    for (int i = 1; i < annotations.size(); i++) {
      EntityAnnotation annotation = annotations.get(i);
      List<Vertex> vertices = annotation.getBoundingPoly().getVerticesList();
      if (vertices.isEmpty()) {
        continue;
      }
      int sumX = 0;
      int sumY = 0;
      for (Vertex vertex : vertices) {
        sumX += vertex.getX();
        sumY += vertex.getY();
        maxX = Math.max(maxX, vertex.getX());
      }
      words.add(
          new Word(annotation.getDescription(), sumX / vertices.size(), sumY / vertices.size()));
    }
    if (words.isEmpty()) {
      return List.of();
    }

    // Vertical midline separates the two player columns.
    int midX =
        response.getFullTextAnnotation().getPagesCount() > 0
            ? response.getFullTextAnnotation().getPages(0).getWidth() / 2
            : maxX / 2;

    List<String> names = new ArrayList<>(extractColumnNames(words, midX, true));
    names.addAll(extractColumnNames(words, midX, false));

    return names.size() > EXPECTED_PLAYER_COUNT ? names.subList(0, EXPECTED_PLAYER_COUNT) : names;
  }

  /**
   * Extracts the player names from a single column (left or right of {@code midX}).
   *
   * @param allWords all detected words
   * @param midX the vertical midline separating the two columns
   * @param leftColumn {@code true} for the left column, {@code false} for the right
   * @return the player names in that column, top to bottom
   */
  private List<String> extractColumnNames(List<Word> allWords, int midX, boolean leftColumn) {
    List<Word> column = new ArrayList<>();
    for (Word word : allWords) {
      if ((word.centerX < midX) == leftColumn) {
        column.add(word);
      }
    }
    column.sort(Comparator.comparingInt(word -> word.centerY));

    // Group words into text lines: consecutive words within a small y-band form one line.
    List<List<Word>> lines = new ArrayList<>();
    for (Word word : column) {
      List<Word> current = lines.isEmpty() ? null : lines.get(lines.size() - 1);
      if (current != null && Math.abs(lineCenterY(current) - word.centerY) <= LINE_Y_TOLERANCE) {
        current.add(word);
      } else {
        List<Word> line = new ArrayList<>();
        line.add(word);
        lines.add(line);
      }
    }

    // Each slot label is immediately followed by that slot's player name. We emit one entry per
    // detected label, falling back to UNKNOWN_PLAYER when the name is unreadable (e.g. "///") so
    // the result still maps 1:1 to the on-screen slots.
    List<String> names = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      if (!isHeroSlotLabel(lines.get(i))) {
        continue;
      }
      String name = "";
      if (i + 1 < lines.size()) {
        List<Word> nameLine = lines.get(i + 1);
        boolean withinGap =
            lineCenterY(nameLine) - lineCenterY(lines.get(i)) <= MAX_NAME_GAP_BELOW_LABEL;
        if (withinGap && !isHeroSlotLabel(nameLine)) {
          name = cleanName(joinLine(nameLine));
        }
      }
      names.add(name.isBlank() ? UNKNOWN_PLAYER : name);
    }
    return names;
  }

  private static int lineCenterY(List<Word> line) {
    int sum = 0;
    for (Word word : line) {
      sum += word.centerY;
    }
    return sum / line.size();
  }

  private static String joinLine(List<Word> line) {
    return line.stream()
        .sorted(Comparator.comparingInt(word -> word.centerX))
        .map(word -> word.text)
        .collect(Collectors.joining(" "));
  }

  private static boolean isHeroSlotLabel(List<Word> line) {
    return HERO_SLOT_LABELS.contains(normalize(joinLine(line)));
  }

  private static String normalize(String value) {
    return value.toUpperCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
  }

  /**
   * Strips the surrounding UI noise from a raw name line: clan tags in brackets ({@code [TAG]}), a
   * leading draft-order number ({@code "4."}), and stray standalone glyphs ({@code ? • ' "}).
   *
   * @param raw the joined text of the name line
   * @return the cleaned player name
   */
  private static String cleanName(String raw) {
    String name = raw;
    name = name.replaceAll("\\[[^\\]]*\\]", " "); // clan tag, e.g. [Лоль]
    name = name.replaceAll("[\\[\\]]", " "); // stray unmatched brackets
    name = name.replaceAll("^\\s*\\d+\\.\\s*", ""); // leading draft-order number, e.g. "4."
    name = name.replaceAll("(?<=^|\\s)[?•'\"]+(?=$|\\s)", " "); // stray standalone glyphs
    return name.replaceAll("\\s+", " ").trim();
  }

  /** A detected word and the centre point of its bounding box. */
  private static final class Word {
    private final String text;
    private final int centerX;
    private final int centerY;

    private Word(String text, int centerX, int centerY) {
      this.text = text;
      this.centerX = centerX;
      this.centerY = centerY;
    }
  }

  /**
   * Returns the image unchanged when it is already under {@link #MAX_IMAGE_SIZE_BYTES}, otherwise
   * re-encodes it as JPEG, progressively lowering quality and then scaling dimensions until it
   * fits.
   *
   * @param imageBytes the raw image bytes
   * @return image bytes guaranteed (best effort) to be under the size limit
   */
  byte[] compressIfNeeded(byte[] imageBytes) {
    if (imageBytes.length < MAX_IMAGE_SIZE_BYTES) {
      return imageBytes;
    }

    log.warn(
        "Image size {} bytes >= limit {} bytes; compressing before sending to Google Vision",
        imageBytes.length,
        MAX_IMAGE_SIZE_BYTES);

    try {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
      if (image == null) {
        log.warn("Unable to decode image for compression; sending original bytes");
        return imageBytes;
      }

      // First pass: keep dimensions, drop JPEG quality.
      for (float quality = 0.85f; quality >= MIN_JPEG_QUALITY; quality -= 0.15f) {
        byte[] encoded = encodeJpeg(image, quality);
        if (encoded.length < MAX_IMAGE_SIZE_BYTES) {
          log.warn("Compressed image to {} bytes at JPEG quality {}", encoded.length, quality);
          return encoded;
        }
      }

      // Second pass: still too big, scale dimensions down by 25% at a time.
      BufferedImage scaled = image;
      for (int attempt = 0; attempt < 6; attempt++) {
        int newWidth = (int) (scaled.getWidth() * 0.75);
        int newHeight = (int) (scaled.getHeight() * 0.75);
        if (newWidth <= 0 || newHeight <= 0) {
          break;
        }
        scaled = scale(scaled, newWidth, newHeight);
        byte[] encoded = encodeJpeg(scaled, MIN_JPEG_QUALITY);
        if (encoded.length < MAX_IMAGE_SIZE_BYTES) {
          log.warn(
              "Compressed image to {} bytes at {}x{} (JPEG quality {})",
              encoded.length,
              newWidth,
              newHeight,
              MIN_JPEG_QUALITY);
          return encoded;
        }
      }

      log.warn("Could not compress image below limit; sending best-effort smallest encoding");
      return encodeJpeg(scaled, MIN_JPEG_QUALITY);
    } catch (IOException e) {
      log.warn("Failed to compress image; sending original bytes", e);
      return imageBytes;
    }
  }

  private byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
    // JPEG has no alpha channel; flatten onto an RGB canvas to avoid encoding failures.
    BufferedImage rgb =
        new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = rgb.createGraphics();
    graphics.drawImage(image, 0, 0, null);
    graphics.dispose();

    ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
    ImageWriteParam param = writer.getDefaultWriteParam();
    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
    param.setCompressionQuality(quality);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
      writer.setOutput(ios);
      writer.write(null, new IIOImage(rgb, null, null), param);
    } finally {
      writer.dispose();
    }
    return out.toByteArray();
  }

  private BufferedImage scale(BufferedImage source, int width, int height) {
    BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = scaled.createGraphics();
    graphics.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    graphics.drawImage(source, 0, 0, width, height, null);
    graphics.dispose();
    return scaled;
  }
}
