package com.axiope.dao.hibernate.audit;

import com.researchspace.model.core.UniquelyIdentifiable;
import java.io.Serializable;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RSEnversUtils implements Serializable {
  /** */
  private static final long serialVersionUID = 358848052642585805L;

  PermanentEntityFilter pef = new PermanentEntityFilter();
  AuditableDeltasFilter adf = new AuditableDeltasFilter();

  void debug(Object entity, String verb) {
    String id = "";
    if (entity instanceof UniquelyIdentifiable) {
      id = ((UniquelyIdentifiable) entity).getOid() + "";
    }
    log.debug("entity of id  {} is being {}-  {} ", id, verb, entity);
  }

  void initFilters(Set<ObjectAuditFilter> filters) {
    filters.add(pef);
    filters.add(adf);
  }
}
