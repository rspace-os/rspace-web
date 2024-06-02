package com.researchspace.offline.service.impl;

import org.apache.commons.lang.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

abstract class MobileContentConverter {

  /**
   * @param fieldContent
   * @param fieldId
   * @return
   */
  public String convertFieldContent(String fieldContent, long fieldId) {
    if (StringUtils.isBlank(fieldContent)) {
      return fieldContent;
    }

    Document d = Jsoup.parse(fieldContent);
    Elements images = d.getElementsByTag("img");

    for (int i = 0; i < images.size(); i++) {
      Element imgElem = images.get(i);

      String newHtml = null;

      if (isRawImageElem(imgElem)) {
        newHtml = convertRawImage(imgElem, fieldId);
      } else if (isAnnotationElem(imgElem)) {
        newHtml = convertAnnotation(imgElem, fieldId);
      } else if (isSketchElem(imgElem)) {
        newHtml = convertSketch(imgElem);
      } else if (isChemImage(imgElem)) {
        newHtml = convertChemImage(imgElem);
      }

      Element newImg = null;
      if (newHtml != null) {
        newImg = Jsoup.parseBodyFragment(newHtml).select("img").first();
        imgElem.replaceWith(newImg);
      }
    }

    return d.body().html();
  }
  ;

  protected abstract boolean isRawImageElem(Element imgElem);

  protected abstract boolean isAnnotationElem(Element imgElem);

  protected abstract boolean isSketchElem(Element imgElem);

  protected abstract boolean isChemImage(Element imgElem);

  protected abstract String convertRawImage(Element imgElem, long fieldId);

  protected abstract String convertAnnotation(Element imgElem, long fieldId);

  protected abstract String convertSketch(Element imgElem);

  protected abstract String convertChemImage(Element imgElem);

  protected int getElemWidth(Element imgElem) {
    if (!imgElem.hasAttr("width")) {
      return 0;
    }
    return (int) Math.round(Double.parseDouble(imgElem.attr("width")));
  }

  protected int getElemHeight(Element imgElem) {
    if (!imgElem.hasAttr("height")) {
      return 0;
    }
    return (int) Math.round(Double.parseDouble(imgElem.attr("height")));
  }
}
