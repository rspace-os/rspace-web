package com.axiope.dao.hibernate.audit;

import java.io.Serializable;
import java.util.Set;

/**
 * If any one filter rejects this object, process() will return <code>false</code>. I.e., this
 * implementation will return <code>true</code> only if all filters permit the object to be audited.
 */
public class RejectIfOneFilterRejectsFilterProcessor implements FilterProcessor, Serializable {

  private static final long serialVersionUID = -6731354486203414123L;

  @Override
  public boolean process(Set<ObjectAuditFilter> toProcess, Object entity) {
    return toProcess.stream().allMatch(f -> f.filter(entity));
  }
}
