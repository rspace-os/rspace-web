package com.researchspace.dao.customliquibaseupdates.v53;

import com.researchspace.dao.AuditDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.dao.SignatureDao;
import com.researchspace.dao.customliquibaseupdates.AbstractCustomLiquibaseUpdater;
import com.researchspace.linkedelements.RichTextUpdater;
import com.researchspace.model.Signature;
import com.researchspace.model.audit.AuditedRecord;
import com.researchspace.model.record.StructuredDocument;
import java.util.Date;
import java.util.List;
import liquibase.database.Database;
import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;

/**
 * With RSPAC-178 the signed documents are pointing to the specific revision of media items.
 *
 * <p>This liquibase update runs RichTextUpdater.updateLinksWithRevisions() method on every signed
 * document. If the field content is modified the field is saved, as is the parent document, with
 * modification date of newly created revision set to the date of signing.
 */
public class UpdateMediaLinksInSignedDocument extends AbstractCustomLiquibaseUpdater {

  private RecordDao recordDao;
  private SignatureDao signatureDao;
  private AuditDao auditDao;
  private RichTextUpdater richTextUpdater;

  private int signedDocsCounter = 0;
  private int updatedDocsCounter = 0;

  @Override
  protected void addBeans() {
    recordDao = context.getBean(RecordDao.class);
    signatureDao = context.getBean(SignatureDao.class);
    auditDao = context.getBean(AuditDao.class);
    richTextUpdater = context.getBean(RichTextUpdater.class);
  }

  @Override
  public String getConfirmationMessage() {
    return "Linked revisions set in "
        + updatedDocsCounter
        + " out of "
        + signedDocsCounter
        + " signed docs.";
  }

  @Override
  protected void doExecute(Database database) {
    logger.info("executing signed documents update");

    List<StructuredDocument> signedDocs = getSignedDocuments();
    signedDocsCounter = signedDocs.size();
    logger.info("There are {} signed documents to process", signedDocsCounter);
    int totalCounter = 0;
    for (StructuredDocument sd : signedDocs) {
      totalCounter++;
      logger.info(
          "{} / {}  -  updated {}: Updating links for doc {} ",
          totalCounter,
          signedDocsCounter,
          updatedDocsCounter,
          sd.getId());
      try {
        updateLinksInDocument(sd);
      } catch (Exception e) {
        logger.warn("error when trying to update links in " + sd.getId() + ", skipping", e);
      }
    }

    logger.info(
        "updated " + updatedDocsCounter + " out of " + signedDocsCounter + " identified fields");
    logger.info("internal link creation finished fine");
  }

  protected List<StructuredDocument> getSignedDocuments() {
    Criteria criteria = sessionFactory.getCurrentSession().createCriteria(StructuredDocument.class);
    criteria.add(Restrictions.eq("signed", Boolean.TRUE));
    criteria.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
    return criteria.list();
  }

  private void updateLinksInDocument(StructuredDocument sd) {
    boolean contentUpdated = false;

    List<AuditedRecord> docRevisions = auditDao.getRevisionsForDocument(sd, null);

    // last unsigned revision number is used for links, first signed revision date for doc
    // modification date
    AuditedRecord lastUnsignedRevision = null;
    if (docRevisions != null) {
      for (AuditedRecord ar : docRevisions) {
        if (ar != null && ar.getEntity() != null && ar.getEntity().isSigned()) {
          break;
        }
        lastUnsignedRevision = ar;
      }
    }

    if (lastUnsignedRevision != null) {
      contentUpdated =
          richTextUpdater.updateLinksWithRevisions(
              sd, lastUnsignedRevision.getRevision().intValue());
    }
    if (contentUpdated) {
      // let modification date be the same as signature date, or last unsigned date if signature not
      // present
      Date signingDate = lastUnsignedRevision.getEntity().getModificationDateAsDate();
      Signature sdSignature = signatureDao.getSignatureByRecordId(sd.getId());
      if (sdSignature != null) {
        signingDate = sdSignature.getSignatureDate();
      }
      sd.setModificationDate(signingDate);
      recordDao.save(sd);
      updatedDocsCounter++;
    }
  }
}
