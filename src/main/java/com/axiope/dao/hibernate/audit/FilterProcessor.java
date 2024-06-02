package com.axiope.dao.hibernate.audit;

import java.util.Set;

/** Defines a policy for how filters should be processed. */
@FunctionalInterface
public interface FilterProcessor {

  /**
   * Runs a set of filters over an entity that has been persisted. Implementations decide how to
   * combine the results of multiple filters.
   *
   * @param toProcess
   * @param entity The entity to progress
   * @return <code>true</code> if this processor decides the objects pass the filters, <code>false
   *     </code> otherwise.
   */
  public boolean process(Set<ObjectAuditFilter> toProcess, Object entity);
}
