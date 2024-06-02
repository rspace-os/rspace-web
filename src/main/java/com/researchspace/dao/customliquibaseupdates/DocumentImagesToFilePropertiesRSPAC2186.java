package com.researchspace.dao.customliquibaseupdates;

import com.researchspace.files.service.FileStore;
import com.researchspace.files.service.InternalFileStore;
import com.researchspace.model.FileProperty;
import com.researchspace.model.User;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.List;
import liquibase.database.Database;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

public class DocumentImagesToFilePropertiesRSPAC2186 extends BlobMigrationBase {

  private FileStore fStore;

  @Override
  protected void addBeans() {
    super.addBeans();
    fStore = context.getBean(InternalFileStore.class);
  }

  @Override
  public String getConfirmationMessage() {
    return "Doc thumbnails transferred";
  }

  protected void doExecute(Database database) {
    final ScrollableResults results =
        sessionFactory.getCurrentSession().createQuery("from User").scroll(ScrollMode.FORWARD_ONLY);
    while (results.next()) {
      User user = (User) results.get(0);
      TransactionStatus nestedTx =
          getTxMger()
              .getTransaction(
                  new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW));
      logger.info("Transferring document thumbnails for user {}", user.getUsername());

      try {
        // get EcatDocs that don't already have a
        List docs =
            sessionFactory
                .getCurrentSession()
                .createSQLQuery(
                    "select edf.id as docId, emf.fileName, iblob.id as blobId, iblob.data from"
                        + " EcatDocumentFile edf inner join BaseRecord br on br.id = edf.id inner"
                        + " join ImageBlob iblob on iblob.id=edf.thumbNail_id inner join"
                        + " EcatMediaFile emf on emf.id=edf.id where br.owner_id=:userId and"
                        + " edf.thumbNail_id is not null and edf.docThumbnailFP_id is null")
                .setParameter("userId", user.getId())
                .list();
        for (int i = 0; i < docs.size(); i++) {
          if (i % 20 == 0) {
            logger.info(" converting {} / {} ", i, docs.size());
          }
          Object[] row = (Object[]) docs.get(i);
          BigInteger docId = (BigInteger) row[0];
          String originalFileName = (String) row[1];
          BigInteger blobId = (BigInteger) row[2];
          byte[] data = (byte[]) row[3];
          FileProperty fp = null;

          String thumbnailFname = pngIfyFileExt(originalFileName);
          try (InputStream is = new ByteArrayInputStream(data)) {
            fp =
                fStore.createAndSaveFileProperty(
                    InternalFileStore.DOC_THUMBNAIL_CATEGORY, user, thumbnailFname, is);
          } catch (IOException e) {
            logger.error("{} thumbnail was not transferred -  continuing", docId);
            continue;
          }
          int rowsModified =
              sessionFactory
                  .getCurrentSession()
                  .createSQLQuery(
                      "update EcatDocumentFile set docThumbnailFP_id = :fpid where id = :id")
                  .setParameter("fpid", fp.getId())
                  .setParameter("id", docId.longValue())
                  .executeUpdate();
          if (rowsModified != 1) {
            logger.error("unexpected row update for {} - {}", docId, rowsModified);
          } else {
            logger.info(
                "moved blob id {} (length {}) for ecatDoc {} to FileProperty {} length {}",
                blobId,
                data.length,
                docId,
                fp.getId(),
                fp.getFileSize());
            if (data.length != Integer.parseInt(fp.getFileSize())) {
              logger.warn("Possible incorrect transfer of doc thumbnail for doc {}", docId);
            }
          }
        }
        logger.info("{} thumbnails transferred  for user {}", docs.size(), user.getUsername());
      } finally {
        getTxMger().commit(nestedTx);
      }
    }
  }
}
