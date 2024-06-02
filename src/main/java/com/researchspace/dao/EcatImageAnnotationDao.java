package com.researchspace.dao;

import com.researchspace.model.EcatImageAnnotation;
import java.util.List;

public interface EcatImageAnnotationDao extends GenericDao<EcatImageAnnotation, Long> {

  EcatImageAnnotation getFromParentIdAndImageId(Long parentId, Long imageId);

  /**
   * Gets a list of image annotations for this field from the database
   *
   * @return copy of this object.
   */
  List<EcatImageAnnotation> getAllImageAnnotationsFromField(Long fieldId);
}
