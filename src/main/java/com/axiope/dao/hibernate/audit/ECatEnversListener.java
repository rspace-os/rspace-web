package com.axiope.dao.hibernate.audit;

import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class extends the standard Envers listener and can be used to filter out any DB events on
 * audited classes that we don't want to archive (e.g., manipulation of temporary data)
 */
public class ECatEnversListener {

  Logger log = LoggerFactory.getLogger(ECatEnversListener.class);

  private Set<ObjectAuditFilter> updateFilters = new HashSet<ObjectAuditFilter>();
  private Set<ObjectAuditFilter> deletedFilters = new HashSet<ObjectAuditFilter>();
  private Set<ObjectAuditFilter> insertFilters = new HashSet<ObjectAuditFilter>();

  /** */
  private static final long serialVersionUID = 1L;

  public ECatEnversListener() {
    initFilters();
  }

  private FilterProcessor fp = new RejectIfOneFilterRejectsFilterProcessor();

  private void initFilters() {
    PermanentEntityFilter pef = new PermanentEntityFilter();
    AuditableDeltasFilter adf = new AuditableDeltasFilter();
    insertFilters.add(pef);
    insertFilters.add(adf);

    updateFilters.add(pef);
    updateFilters.add(adf);

    deletedFilters.add(pef);
    deletedFilters.add(adf);
  }
}
