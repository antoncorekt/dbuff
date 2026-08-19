package com.ako.dbuff.service;

import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.BoundingPoly;
import com.google.cloud.vision.v1.EntityAnnotation;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.Vertex;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageProcessorTest {

  @Mock private ImageAnnotatorClient imageAnnotatorClient;

  private ImageProcessor imageProcessor;

  @BeforeEach
  void setUp() {
    imageProcessor = new ImageProcessor(Optional.of(imageAnnotatorClient));
  }

  @Test
  void compressIfNeeded_smallImage_returnedUnchanged() {
    byte[] small = new byte[] {1, 2, 3, 4, 5};

    byte[] result = imageProcessor.compressIfNeeded(small);

    assertSame(small, result, "Images under the size limit must be returned unchanged");
  }

  @Test
  void compressIfNeeded_largeImage_compressedBelowLimit() throws IOException {
    byte[] large = buildLargePng();
    assertTrue(
        large.length >= ImageProcessor.MAX_IMAGE_SIZE_BYTES,
        "Test fixture must exceed the size limit; was " + large.length + " bytes");

    byte[] result = imageProcessor.compressIfNeeded(large);

    assertTrue(
        result.length < ImageProcessor.MAX_IMAGE_SIZE_BYTES,
        "Compressed image must be under the limit; was " + result.length + " bytes");
    assertTrue(result.length < large.length, "Compressed image must be smaller than the original");
  }

  @Test
  void extractPlayerNames_usesSlotLabelsAndColumnsAndStripsNoise() {
    // Two columns of "ГЕРОЯ НЕТ" (NO HERO) slot labels, each with a name directly beneath it.
    // Mirrors the real Ability Draft layout, including clan tags, a draft-order number and a
    // stray "?" glyph that must all be stripped, plus multi-word Cyrillic names.
    List<EntityAnnotation> annotations = new ArrayList<>();
    annotations.add(word("full text block", 0, 0)); // index 0 is the whole-image block, skipped

    // Left column (x ~ 20-45).
    annotations.add(word("ГЕРОЯ", 20, 10));
    annotations.add(word("НЕТ", 35, 10));
    annotations.add(word("Chupapi", 25, 30));
    annotations.add(word("ГЕРОЯ", 20, 60));
    annotations.add(word("НЕТ", 35, 60));
    annotations.add(word("TiT", 22, 80));
    annotations.add(word("[", 30, 80));
    annotations.add(word("ezFM", 33, 80));
    annotations.add(word("]", 40, 80));
    // A slot label whose name ("///") OCR does not emit -> falls back to UNKNOWN.
    annotations.add(word("ГЕРОЯ", 20, 110));
    annotations.add(word("НЕТ", 35, 110));

    // Right column (x ~ 160-195).
    annotations.add(word("ГЕРОЯ", 160, 10));
    annotations.add(word("НЕТ", 180, 10));
    annotations.add(word("Лолец", 160, 30));
    annotations.add(word("пастухов", 175, 30));
    annotations.add(word("[", 188, 30));
    annotations.add(word("Лоль", 190, 30));
    annotations.add(word("]", 194, 30));
    annotations.add(word("ГЕРОЯ", 160, 60));
    annotations.add(word("НЕТ", 180, 60));
    annotations.add(word("4.", 158, 80));
    annotations.add(word("Пастух", 162, 80));
    annotations.add(word("лолей", 178, 80));
    annotations.add(word("?", 195, 82));

    stubVisionResponse(annotations);

    List<String> names = imageProcessor.extractPlayerNames(new byte[] {1, 2, 3});

    assertEquals(
        List.of("Chupapi", "TiT", "UNKNOWN", "Лолец пастухов", "Пастух лолей"),
        names,
        "Names come from the line under each slot label, left column first, with clan tags,"
            + " draft-order numbers and stray glyphs stripped; unreadable slots become UNKNOWN");
  }

  @Test
  void extractPlayerNames_noVisionClient_failsPerRequestWithActionableMessage() {
    // A missing or unusable Google credential leaves the client absent rather than failing the
    // application context, so OCR must degrade to a per-request error.
    ImageProcessor withoutClient = new ImageProcessor(Optional.empty());

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class, () -> withoutClient.extractPlayerNames(new byte[] {1}));

    assertTrue(
        thrown.getMessage().contains("google.vision.credentials-path"),
        "Message must name the property to set; was: " + thrown.getMessage());
  }

  private void stubVisionResponse(List<EntityAnnotation> annotations) {
    AnnotateImageResponse response =
        AnnotateImageResponse.newBuilder().addAllTextAnnotations(annotations).build();
    BatchAnnotateImagesResponse batchResponse =
        BatchAnnotateImagesResponse.newBuilder().addResponses(response).build();
    when(imageAnnotatorClient.batchAnnotateImages(anyList())).thenReturn(batchResponse);
  }

  /** Builds a Vision word annotation with a ~10x5 bounding box anchored at (x, y). */
  private EntityAnnotation word(String text, int x, int y) {
    BoundingPoly poly =
        BoundingPoly.newBuilder()
            .addVertices(Vertex.newBuilder().setX(x).setY(y).build())
            .addVertices(Vertex.newBuilder().setX(x + 10).setY(y).build())
            .addVertices(Vertex.newBuilder().setX(x + 10).setY(y + 5).build())
            .addVertices(Vertex.newBuilder().setX(x).setY(y + 5).build())
            .build();
    return EntityAnnotation.newBuilder().setDescription(text).setBoundingPoly(poly).build();
  }

  /** Builds a PNG large enough to exceed the size limit (random noise resists compression). */
  private byte[] buildLargePng() throws IOException {
    int size = 3000;
    BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
    Random random = new Random(42);
    for (int x = 0; x < size; x++) {
      for (int y = 0; y < size; y++) {
        image.setRGB(x, y, new Color(random.nextInt()).getRGB());
      }
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }
}
