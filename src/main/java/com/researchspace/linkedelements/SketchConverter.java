package com.researchspace.linkedelements;

import com.researchspace.dao.EcatImageAnnotationDao;
import com.researchspace.model.EcatImageAnnotation;
import java.util.Optional;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;

class SketchConverter extends AbstractFieldElementConverter implements FieldElementConverter {

  private @Autowired EcatImageAnnotationDao ecatImageAnnotationDao;

  public Optional<EcatImageAnnotation> jsoup2LinkableElement(FieldContents contents, Element el) {
    Optional<EcatImageAnnotation> ecatImageAnnotation = Optional.empty();
    if (el.hasAttr("id")) {
      long id = Long.parseLong(el.attr("id"));
      ecatImageAnnotation = getItem(id, null, EcatImageAnnotation.class, ecatImageAnnotationDao);
      if (ecatImageAnnotation.isPresent()) {
        contents.addSketch(ecatImageAnnotation.get(), el.attr("src"));
      } else {
        logNotFound("Sketch", id);
      }
    }
    return ecatImageAnnotation;
  }
}
