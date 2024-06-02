package com.researchspace.offline.service.impl;

import com.researchspace.linkedelements.RichTextUpdater;
import com.researchspace.model.EcatImageAnnotation;
import com.researchspace.service.EcatImageAnnotationManager;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("rsToMobileConverter")
public class RsToMobileContentConverter extends MobileContentConverter {

  @Autowired private RichTextUpdater richTextUpdater;

  @Autowired private MobileRichTextUpdater mobileRichTextUpdater;

  private EcatImageAnnotationManager ecatImageAnnotationManager;

  @Autowired
  public void setEcatImageAnnotationManager(EcatImageAnnotationManager ecatImageAnnotationManager) {
    this.ecatImageAnnotationManager = ecatImageAnnotationManager;
  }

  @Override
  protected boolean isRawImageElem(Element imgElem) {
    return richTextUpdater.isRawImage(imgElem);
  }

  @Override
  protected boolean isAnnotationElem(Element imgElem) {
    return richTextUpdater.isImageOrAnnotation(imgElem);
  }

  @Override
  protected boolean isSketchElem(Element imgElem) {
    return richTextUpdater.isSketch(imgElem);
  }

  @Override
  protected boolean isChemImage(Element imgElem) {
    return richTextUpdater.isChemImage(imgElem);
  }

  @Override
  protected String convertRawImage(Element imgElem, long fieldId) {

    long imageId = getIdFromComposition(imgElem);
    int imgWidth = getElemWidth(imgElem);
    int imgHeight = getElemHeight(imgElem);

    return mobileRichTextUpdater.generateMobileRawImg(imageId, imgWidth, imgHeight);
  }

  @Override
  protected String convertAnnotation(Element imgElem, long fieldId) {

    long imageId = getIdFromComposition(imgElem);
    int imgWidth = getElemWidth(imgElem);
    int imgHeight = getElemHeight(imgElem);

    EcatImageAnnotation annotation =
        ecatImageAnnotationManager.getByParentIdAndImageId(fieldId, imageId, null);

    return mobileRichTextUpdater.generateMobileAnnotationImg(
        annotation.getId(), imageId, imgWidth, imgHeight);
  }

  @Override
  protected String convertSketch(Element imgElem) {

    long sketchId = getElemId(imgElem);
    int imgWidth = getElemWidth(imgElem);
    int imgHeight = getElemHeight(imgElem);

    return mobileRichTextUpdater.generateMobileSketchImg(sketchId, imgWidth, imgHeight);
  }

  @Override
  protected String convertChemImage(Element imgElem) {

    long chemId = getElemId(imgElem);
    int imgWidth = getElemWidth(imgElem);
    int imgHeight = getElemHeight(imgElem);
    long fieldId = getChemFieldId(imgElem);

    return mobileRichTextUpdater.generateMobileChemImg(chemId, imgWidth, imgHeight, fieldId);
  }

  final Pattern p = Pattern.compile("sourceParentId=(\\d+)");

  private Long getChemFieldId(Element imgElem) {
    String src = imgElem.attr("src");
    Matcher m = p.matcher(src);
    if (m.find()) {
      return Long.parseLong(m.group(1));
    } else {
      return null;
    }
  }

  private long getIdFromComposition(Element el) {
    if (el.hasAttr("id")) {
      String[] ids = el.attr("id").split("-");
      if (ids.length > 1) {
        return Long.parseLong(ids[1]);
      }
    }
    return -1;
  }

  protected long getElemId(Element imgElem) {
    if (!imgElem.hasAttr("id")) {
      return -1;
    }
    return Long.parseLong(imgElem.attr("id"));
  }
}
