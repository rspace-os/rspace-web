package com.researchspace.dao;

import com.researchspace.model.RSMath;
import java.util.List;

/** Math element */
public interface RSMathDao extends GenericDao<RSMath, Long> {

  /**
   * @param fieldId the db identifier of the field
   * @return A possibly empty but non-<code>null</code> list of RSMath objects for this field.
   */
  List<RSMath> getAllMathElementsFromField(Long fieldId);
}
