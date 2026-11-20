package com.researchspace.export.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.w3c.dom.Element;
import org.xhtmlrenderer.extend.ReplacedElement;
import org.xhtmlrenderer.render.BlockBox;

@ExtendWith(MockitoExtension.class)
class HtmlImageResolverTest {

  private static final int DOTS_PER_PIXEL = 20;

  @Mock private ImageRetrieverHelper imageRetrieverHelper;
  @Mock private BlockBox blockBox;
  @InjectMocks private HtmlImageResolver resolver;

  @ParameterizedTest(name = "{0}, annotations={1}")
  @CsvSource({
    // excluded annotation: the square original keeps its proportions inside the wider canvas box
    "/image/getAnnotation/5/1, false, 350, 350, 1.0",
    "/image/getAnnotation/5/1, true, 525, 350, 1.5",
    "/image/getImageSketch/5/1, false, 525, 350, 1.5",
  })
  void imageFillsAnnotationCanvasBoxOnlyWithItsOwnProportions(
      String src,
      boolean annotations,
      int retrievedWidth,
      int retrievedHeight,
      double expectedRatio)
      throws IOException, ParserConfigurationException {
    ExportToFileConfig config = new ExportToFileConfig();
    config.setAnnotations(annotations);
    resolver.setExportConfig(config);
    resolver.setDotsPerPixel(DOTS_PER_PIXEL);
    when(blockBox.getElement()).thenReturn(img(src));
    when(imageRetrieverHelper.getImageBytesFromImgSrc(eq(src), any()))
        .thenReturn(png(retrievedWidth, retrievedHeight));

    ReplacedElement element =
        resolver.createReplacedElement(
            null, blockBox, null, 525 * DOTS_PER_PIXEL, 350 * DOTS_PER_PIXEL);

    assertEquals(
        expectedRatio, (double) element.getIntrinsicWidth() / element.getIntrinsicHeight(), 0.01);
  }

  private static Element img(String src) throws ParserConfigurationException {
    Element img =
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .newDocument()
            .createElement("img");
    img.setAttribute("src", src);
    return img;
  }

  private static byte[] png(int width, int height) throws IOException {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
      return out.toByteArray();
    }
  }
}
