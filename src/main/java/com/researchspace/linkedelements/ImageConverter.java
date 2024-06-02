package com.researchspace.linkedelements;

import com.researchspace.core.util.FieldParserConstants;
import com.researchspace.dao.EcatImageAnnotationDao;
import com.researchspace.dao.EcatImageDao;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatImageAnnotation;
import com.researchspace.model.Thumbnail;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
class ImageConverter extends AbstractFieldElementConverter implements FieldElementConverter {

  private @Autowired EcatImageAnnotationDao ecatImageAnnotationDao;
  private @Autowired EcatImageDao ecatImageDao;

  public Optional<EcatImage> jsoup2LinkableElement(FieldContents contents, Element el) {
    long id = getElementIdFromComposition(el);
    Long revision = getRevisionFromElem(el);

    Optional<EcatImage> rc = Optional.empty();
    if (id != -1) {
      rc = getItem(id, revision, EcatImage.class, ecatImageDao);
      if (rc.isPresent()) {
        contents.addElement(rc.get(), el.attr("src"), EcatImage.class);
        // if image has annotations
        EcatImageAnnotation ecatImageAnnotation = getImageAnnotationFromElement(el);
        if (ecatImageAnnotation != null) {
          contents.addImageAnnotation(ecatImageAnnotation, el.attr("src"));
          log.debug("ARC: {}", el.attr("src"));
        }
      } else {
        logNotFound("Image", id);
      }
    }
    // now add thumbnails to list
    String src = el.attr("src");

    if (src.contains("/thumbnail")) {
      Thumbnail thumb = Thumbnail.fromURL(src);
      if (thumb != null) {
        contents.addThumbnail(thumb);
      }
    }
    return rc;
  }

  /*
   * method retrieves new-style annotations by their id, and old-style annotations by
   * combination of parent and image id
   */
  EcatImageAnnotation getImageAnnotationFromElement(Element el) {
    EcatImageAnnotation annotation = null;
    if (el.hasAttr("data-id")
        && el.hasAttr("data-type")
        && FieldParserConstants.DATA_TYPE_ANNOTATION.equals(el.attr("data-type"))) {
      try {
        Long parsedId = Long.valueOf(el.attr("data-id"));
        Optional<EcatImageAnnotation> optAnnotation =
            getItem(parsedId, null, EcatImageAnnotation.class, ecatImageAnnotationDao);
        if (optAnnotation.isPresent()) {
          annotation = optAnnotation.get();
        } else {
          log.warn("image annotation missing for element with id: {}", parsedId);
        }
      } catch (NumberFormatException nfe) {
        log.warn("unparsable data-id: " + el.attr("data-id"));
      }
    } else {
      long imageId = getElementIdFromComposition(el);
      long fieldId = getFieldIdFromComposition(el);
      annotation = getImageAnnotationByParentAndImageId(fieldId, imageId);
    }
    return annotation;
  }

  private EcatImageAnnotation getImageAnnotationByParentAndImageId(long parentId, long imageId) {
    try {
      return ecatImageAnnotationDao.getFromParentIdAndImageId(parentId, imageId);
    } catch (Exception e) {
      log.warn("couldn't retrieve annotation for " + parentId + "/" + imageId, e);
      return null;
    }
  }
}
