package com.researchspace.dao.customliquibaseupdates;

import com.researchspace.core.util.FieldParserConstants;
import com.researchspace.model.field.Field;
import com.researchspace.service.NfsFieldLinkManager;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;
import java.util.List;
import java.util.stream.Stream;
import liquibase.database.Database;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hibernate.exception.JDBCConnectionException;
import org.springframework.transaction.CannotCreateTransactionException;
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
    if (!anyFilestoreSaved()) {
      logger.info("No filestores, so no filestore links to index");
      return;
    }
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
              // as a live autosave does, the rows follow the pending autosave when there is one
              Field pending = field.getTempField();
              nfsFieldLinkManager.reindex(
                  field, pending != null ? pending.getFieldData() : field.getFieldData());
            });
        indexed++;
      } catch (RuntimeException e) {
        if (databaseUnreachable(e)) {
          // failing the changeset makes it run again at the next start; reindexing is idempotent
          throw e;
        }
        logger.warn("Filestore links of field {} not indexed", fieldId, e);
      }
      if (++processed % PROGRESS_INTERVAL == 0) {
        logger.info("Filestore links: {}/{} fields processed", processed, candidates);
      }
    }
    logger.info(getConfirmationMessage());
  }

  private static boolean databaseUnreachable(Throwable e) {
    return Stream.of(
            CannotCreateTransactionException.class,
            JDBCConnectionException.class,
            SQLTransientConnectionException.class,
            SQLNonTransientConnectionException.class,
            SQLRecoverableException.class)
        .anyMatch(type -> ExceptionUtils.indexOfType(e, type) >= 0);
  }

  /** Any filestore, removed ones included: without one no link can be indexed. */
  boolean anyFilestoreSaved() {
    return !sessionFactory
        .getCurrentSession()
        .createQuery("select s.id from NfsFileStore s", Long.class)
        .setMaxResults(1)
        .list()
        .isEmpty();
  }

  /**
   * Ids only, so the run holds no field entities. Autosave copies have no document, so they are
   * reached through the field they belong to, including when only the copy links a filestore.
   */
  List<Long> candidateFieldIds() {
    return sessionFactory
        .getCurrentSession()
        .createQuery(
            "select f.id from Field f left join f.tempField t"
                + " where f.structuredDocument is not null"
                + " and (f.rtfData like :link or t.rtfData like :link)",
            Long.class)
        .setParameter("link", "%" + FieldParserConstants.NET_FS_CLASSNAME + "%")
        .list();
  }
}
