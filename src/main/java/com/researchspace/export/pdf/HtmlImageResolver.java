package com.researchspace.export.pdf;

import com.lowagie.text.BadElementException;
import com.lowagie.text.Image;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Element;
import org.xhtmlrenderer.extend.FSImage;
import org.xhtmlrenderer.extend.ReplacedElement;
import org.xhtmlrenderer.extend.ReplacedElementFactory;
import org.xhtmlrenderer.extend.UserAgentCallback;
import org.xhtmlrenderer.layout.LayoutContext;
import org.xhtmlrenderer.pdf.ITextFSImage;
import org.xhtmlrenderer.pdf.ITextImageElement;
import org.xhtmlrenderer.render.BlockBox;
import org.xhtmlrenderer.simple.extend.FormSubmissionListener;

/**
 * When an img tag such as <img src="a/path/img.png"> is encountered, replace with the bytes of the
 * image at "a/path/img.png"
 */
@Service
@Slf4j
public class HtmlImageResolver implements ReplacedElementFactory {

  private @Autowired ImageRetrieverHelper imageRetrieverHelper;

  private ReplacedElementFactory superFactory;

  private ExportToFileConfig exportConfig;

  private int dotsPerPixel;

  public void setDotsPerPixel(int dotsPerPixel) {
    this.dotsPerPixel = dotsPerPixel;
  }

  public void setExportConfig(ExportToFileConfig exportConfig) {
    this.exportConfig = exportConfig;
  }

  public void setReplacedElementFactory(ReplacedElementFactory superFactory) {
    this.superFactory = superFactory;
  }

  @Override
  public ReplacedElement createReplacedElement(
      LayoutContext layoutContext,
      BlockBox blockBox,
      UserAgentCallback userAgentCallback,
      int cssWidth,
      int cssHeight) {
    Element element = blockBox.getElement();

    if (element == null) {
      return null;
    }

    String nodeName = element.getNodeName();
    if ("img".equals(nodeName)) {
      if (!element.hasAttribute("src")) {
        log.warn("An img element is missing a `src` attribute so cannot locate image.");
        return null;
      }

      String src = element.getAttribute("src");
      if (src.startsWith("data:image/")) {
        if (src.contains(";base64,")) {
          // tinymce seems to allow encoded images with spaces (which it must strip out) which
          // aren't valid in base64, so remove any spaces.
          element.setAttribute("src", src.replaceAll("\\s", ""));
        }
        // image is already embedded so does not need to be retrieved
        return this.superFactory.createReplacedElement(
            layoutContext, blockBox, userAgentCallback, cssWidth, cssHeight);
      }

      try {
        byte[] imageBytes =
            imageRetrieverHelper.getImageBytesFromImgSrc(element.getAttribute("src"), exportConfig);
        if (imageBytes == null) {
          return null;
        }
        final Image image = Image.getInstance(imageBytes);
        image.scaleAbsolute(
            image.getPlainWidth() * dotsPerPixel, image.getPlainHeight() * dotsPerPixel);
        final FSImage fsImage = new ITextFSImage(image);
        if (fsImage != null) {
          if ((cssWidth != -1) || (cssHeight != -1)) {
            fsImage.scale(cssWidth, cssHeight);
          }
          return new ITextImageElement(fsImage);
        }
      } catch (IOException | BadElementException e) {
        log.warn(String.format("Problem adding image %s to pdf.", src), e);
        return null;
      }
    }
    return this.superFactory.createReplacedElement(
        layoutContext, blockBox, userAgentCallback, cssWidth, cssHeight);
  }

  @Override
  public void reset() {
    this.superFactory.reset();
  }

  @Override
  public void remove(Element e) {
    this.superFactory.remove(e);
  }

  @Override
  public void setFormSubmissionListener(FormSubmissionListener listener) {
    this.superFactory.setFormSubmissionListener(listener);
  }
}
