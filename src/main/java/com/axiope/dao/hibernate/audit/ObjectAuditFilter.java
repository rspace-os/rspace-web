package com.axiope.dao.hibernate.audit;

import com.researchspace.core.util.CollectionFilter;
import java.io.Serializable;

/**
 * Filter interface to define policy for what should / should not be audited. To add a new Audit
 * Filter, implement this interface and add it to the list of Filter in {@link
 * ECatEnversListener}#init.
 */
public abstract class ObjectAuditFilter implements CollectionFilter<Object>, Serializable {

  /** */
  private static final long serialVersionUID = 1L;

  /**
   * If should be audited, return true, else return false
   *
   * @param entity
   * @return <code>true</code> if entity should be audited, false otherwise
   */
  public abstract boolean filter(Object entity);
}
