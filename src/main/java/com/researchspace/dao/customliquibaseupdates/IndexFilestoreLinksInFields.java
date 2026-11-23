package com.researchspace.dao.customliquibaseupdates;

import com.researchspace.core.util.FieldParserConstants;
import com.researchspace.model.field.Field;
import com.researchspace.service.NfsFieldLinkManager;
import java.util.List;
import liquibase.database.Database;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Indexes, once, the filestore links that documents already held before the link index. */
public class IndexFilestoreLinksInFields extends AbstractCustomLiquibaseUpdater {

  private static final int PROGRESS_INTERVAL = 500;

  NfsFieldLinkManager nfsFieldLinkManager;
  TransactionTemplate fieldTransaction;
  private int candidates;
  private int indexed;

  @Override
  protected void addBeans() {
    nfsFieldLinkManager = context.getBean(NfsFieldLinkManager.class);
    fieldTransaction = new TransactionTemplate(getTxMger());
    fieldTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public String getConfirmationMessage() {
    return "Filestore links indexed for " + indexed + "/" + candidates + " fields";
  }

  @Override
  protected void doExecute(Database database) {
    List<Long> fieldIds = candidateFieldIds();
    candidates = fieldIds.size();
    logger.info("{} fields with filestore links to index", candidates);
    int processed = 0;
    for (Long fieldId : fieldIds) {
      try {
        // a throw inside the transactional manager marks the enclosing transaction rollback-only,
        // so each field gets its own and a failing one rolls back alone
        fieldTransaction.executeWithoutResult(
            status -> {
              Field field = fMger.get(fieldId, null).orElseThrow();
              nfsFieldLinkManager.reindex(field, field.getFieldData());
            });
        indexed++;
      } catch (RuntimeException e) {
        logger.warn("Filestore links of field {} not indexed", fieldId, e);
      }
      if (++processed % PROGRESS_INTERVAL == 0) {
        logger.info("Filestore links: {}/{} fields processed", processed, candidates);
      }
    }
    logger.info(getConfirmationMessage());
  }

  /** Ids only, so the run holds no field entities; autosave copies have no document. */
  List<Long> candidateFieldIds() {
    return sessionFactory
        .getCurrentSession()
        .createQuery(
            "select f.id from Field f where f.structuredDocument is not null"
                + " and f.rtfData like :link",
            Long.class)
        .setParameter("link", "%" + FieldParserConstants.NET_FS_CLASSNAME + "%")
        .list();
  }
}
