package com.researchspace.export.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.w3c.dom.Element;
import org.xhtmlrenderer.extend.ReplacedElement;
import org.xhtmlrenderer.layout.LayoutContext;
import org.xhtmlrenderer.render.BlockBox;

@ExtendWith(MockitoExtension.class)
class HtmlImageResolverTest {

  private static final String SRC = "/image/getImage/1-2/3";

  @Mock private ImageRetrieverHelper imageRetrieverHelper;
  @Mock private LayoutContext layoutContext;
  @Mock private BlockBox blockBox;

  @InjectMocks private HtmlImageResolver resolver;

  // RSDEV-1014: chemistry images exported with width="90%" overflowed the PDF page
  @Test
  void scalesRetrievedImageToCssWidthKeepingAspectRatio() throws Exception {
    ExportToFileConfig config = new ExportToFileConfig();
    resolver.setExportConfig(config);
    resolver.setDotsPerPixel(1);
    when(blockBox.getElement()).thenReturn(imgElement());
    when(imageRetrieverHelper.getImageBytesFromImgSrc(SRC, config)).thenReturn(png(400, 200));

    ReplacedElement replaced =
        resolver.createReplacedElement(layoutContext, blockBox, null, 100, -1);

    assertEquals(100, replaced.getIntrinsicWidth());
    assertEquals(50, replaced.getIntrinsicHeight());
  }

  private static Element imgElement() throws Exception {
    Element img =
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .newDocument()
            .createElement("img");
    img.setAttribute("src", SRC);
    return img;
  }

  private static byte[] png(int width, int height) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
    return out.toByteArray();
  }
}
