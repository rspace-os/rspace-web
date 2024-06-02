package com.researchspace.offline.service.impl;

import java.util.HashMap;
import java.util.Map;
import org.apache.velocity.app.VelocityEngine;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.ui.velocity.VelocityEngineUtils;

/**
 * Updates links to images in a {@link TextField} to match the format used by the mobile
 * application.
 *
 * @see {@link FieldParser} for documentation on the syntax of the links used in text fields.
 */
@Component("mobileRichTextUpdater")
public class MobileRichTextUpdater {

  private static final Logger log = LoggerFactory.getLogger(MobileRichTextUpdater.class);

  /** value for data-type attribute of img tag indicating an image */
  public static final String MOBILE_DATA_TYPE_IMAGE = "image";

  /** value for data-type attribute of img tag indicating an annotation */
  public static final String MOBILE_DATA_TYPE_ANNOTATION = "annotation";

  /** value for data-type attribute of img tag indicating a sketch */
  public static final String MOBILE_DATA_TYPE_SKETCH = "sketch";

  /** value for data-type attribute of img tag indicating a sketch */
  public static final String MOBILE_DATA_TYPE_CHEM = "chem";

  @Autowired private VelocityEngine velocity;

  public boolean isMobileRawImage(Element el) {
    return getDataTypeAttr(el).equals(MOBILE_DATA_TYPE_IMAGE);
  }

  public boolean isMobileAnnotation(Element el) {
    return getDataTypeAttr(el).equals(MOBILE_DATA_TYPE_ANNOTATION);
  }

  public boolean isMobileSketch(Element el) {
    return getDataTypeAttr(el).equals(MOBILE_DATA_TYPE_SKETCH);
  }

  public boolean isMobileChemImage(Element el) {
    return getDataTypeAttr(el).equals(MOBILE_DATA_TYPE_CHEM);
  }

  private String getDataTypeAttr(Element el) {
    if (!el.hasAttr("data-type")) {
      return "";
    }
    return el.attr("data-type");
  }

  public String generateMobileRawImg(long imageId, int width, int height) {
    Map<String, Object> velocityModel = new HashMap<String, Object>();
    velocityModel.put("imageId", imageId);
    velocityModel.put("width", width);
    velocityModel.put("height", height);

    String msg =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocity, "rawImageLinkMobile.vm", "UTF-8", velocityModel);
    return msg;
  }

  public String generateMobileAnnotationImg(
      long annotationId, long imageId, int width, int height) {
    Map<String, Object> velocityModel = new HashMap<String, Object>();
    velocityModel.put("annotationId", annotationId);
    velocityModel.put("imageId", imageId);
    velocityModel.put("width", width);
    velocityModel.put("height", height);

    String msg =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocity, "annotatedImageLinkMobile.vm", "UTF-8", velocityModel);
    return msg;
  }

  public String generateMobileSketchImg(long sketchId, int width, int height) {
    Map<String, Object> velocityModel = new HashMap<String, Object>();
    velocityModel.put("sketchId", sketchId);
    velocityModel.put("width", width);
    velocityModel.put("height", height);

    String msg =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocity, "sketchLinkMobile.vm", "UTF-8", velocityModel);
    return msg;
  }

  public String generateMobileChemImg(long chemId, int width, int height, long fieldId) {
    Map<String, Object> velocityModel = new HashMap<String, Object>();
    velocityModel.put("chemId", chemId);
    velocityModel.put("width", width);
    velocityModel.put("height", height);
    velocityModel.put("fieldId", fieldId);

    String msg =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocity, "chemLinkMobile.vm", "UTF-8", velocityModel);
    return msg;
  }
}
