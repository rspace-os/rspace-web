package com.researchspace.offline.service.impl;

import com.researchspace.dao.EcatImageDao;
import com.researchspace.dao.RSChemElementDao;
import com.researchspace.linkedelements.RichTextUpdater;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatImageAnnotation;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("mobileToRsConverter")
public class MobileToRsContentConverter extends MobileContentConverter {

  @Autowired private RichTextUpdater richTextUpdater;

  @Autowired private MobileRichTextUpdater mobileRichTextUpdater;

  @Autowired private EcatImageDao ecatImageDao;

  @Autowired private RSChemElementDao rsChemElementDao;

  @Override
  protected boolean isRawImageElem(Element imgElem) {
    return mobileRichTextUpdater.isMobileRawImage(imgElem);
  }

  @Override
  protected boolean isAnnotationElem(Element imgElem) {
    return mobileRichTextUpdater.isMobileAnnotation(imgElem);
  }

  @Override
  protected boolean isSketchElem(Element imgElem) {
    return mobileRichTextUpdater.isMobileSketch(imgElem);
  }

  @Override
  protected boolean isChemImage(Element imgElem) {
    return mobileRichTextUpdater.isMobileChemImage(imgElem);
  }

  @Override
  protected String convertRawImage(Element imgElem, long fieldId) {

    long imageId = getAttrAsLong(imgElem, "data-id");

    /* we reuse existing database image in case it differs (i.e. mobile format doesn't use name attribute) */
    EcatImage dbImage = ecatImageDao.get(imageId);
    if (dbImage == null) {
      throw new IllegalArgumentException("no image found for id: " + imageId);
    }

    // as only thing that could change is width and height
    dbImage.setWidth(getElemWidth(imgElem));
    dbImage.setHeight(getElemHeight(imgElem));

    return richTextUpdater.generateRawImageElement(dbImage, "" + fieldId);
  }

  @Override
  protected String convertAnnotation(Element imgElem, long fieldId) {
    String newHtml;
    EcatImageAnnotation newAnnotation = new EcatImageAnnotation();
    int newWidth = getElemWidth(imgElem);
    int newHeight = getElemHeight(imgElem);

    newAnnotation.setId(getAttrAsLong(imgElem, "data-id"));
    newAnnotation.setImageId(getAttrAsLong(imgElem, "data-imageid"));
    newAnnotation.setWidth(newWidth);
    newAnnotation.setHeight(newHeight);

    // annotated image replacement
    newHtml = richTextUpdater.generateAnnotatedImageElement(newAnnotation, "" + fieldId);
    return newHtml;
  }

  @Override
  protected String convertSketch(Element imgElem) {
    String newHtml;
    EcatImageAnnotation newSketch = new EcatImageAnnotation();
    int newWidth = getElemWidth(imgElem);
    int newHeight = getElemHeight(imgElem);

    newSketch.setId(getAttrAsLong(imgElem, "data-id"));
    newSketch.setWidth(newWidth);
    newSketch.setHeight(newHeight);

    // sketch replacement
    newHtml = richTextUpdater.generateImgLinkForSketch(newSketch);
    return newHtml;
  }

  @Override
  protected String convertChemImage(Element imgElem) {

    long chemId = getAttrAsLong(imgElem, "data-id");
    int newWidth = getElemWidth(imgElem);
    int newHeight = getElemHeight(imgElem);
    long fieldId = getAttrAsLong(imgElem, "data-fieldId");
    return richTextUpdater.generateURLStringForRSChemElementLink(
        chemId, fieldId, newWidth, newHeight);
  }

  protected long getAttrAsLong(Element imgElem, String attr) {
    if (!imgElem.hasAttr(attr)) {
      throw new IllegalArgumentException("no " + attr + " on image element: " + imgElem);
    }
    return Long.parseLong(imgElem.attr(attr));
  }
}
