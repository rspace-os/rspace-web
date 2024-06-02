package com.researchspace.dao.customliquibaseupdates;

import com.researchspace.files.service.FileStore;
import com.researchspace.files.service.InternalFileStore;
import com.researchspace.model.FileProperty;
import com.researchspace.model.User;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

// reusable helper methods
abstract class BlobMigrationBase extends AbstractCustomLiquibaseUpdater {

  private FileStore fStore;

  @Override
  protected void addBeans() {
    super.addBeans();
    fStore = context.getBean(InternalFileStore.class);
  }

  // replaces original filename suffix, or returns unchanged if already a PNG
  String pngIfyFileExt(String originalFileName) {
    String fileExt = FilenameUtils.getExtension(originalFileName);
    if ("png".equalsIgnoreCase(fileExt)) {
      return originalFileName;
    }
    String thumbnailFname = "";
    if (!StringUtils.isBlank(fileExt)) {
      thumbnailFname = originalFileName.replace("." + fileExt, ".png");
    } else {
      thumbnailFname = originalFileName + ".png";
    }
    return thumbnailFname;
  }

  void migrate(
      final String query,
      final String fsCategory,
      final String updateSQL,
      final String progressLogMsg) {
    final ScrollableResults results =
        sessionFactory.getCurrentSession().createQuery("from User").scroll(ScrollMode.FORWARD_ONLY);
    // iterating over users is a convenient, fairly fine-grained way to demarcate transaction
    // boundaries -
    // in the event of a crash, some file copying will be duplicated but only for 1 user.
    while (results.next()) {
      // reuse file property for imageblobs used multiple times
      Map<Long, FileProperty> blobIdToFileProperty = new HashMap<>();
      User user = (User) results.get(0);
      TransactionStatus nestedTx =
          getTxMger()
              .getTransaction(
                  new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW));
      logger.info("{} for user {}", progressLogMsg, user.getUsername());
      try {

        List docs =
            sessionFactory
                .getCurrentSession()
                .createSQLQuery(query)
                .setParameter("userId", user.getId())
                .list();
        for (int i = 0; i < docs.size(); i++) {
          if (i % 20 == 0) {
            logger.info("converting {} / {} ", i, docs.size());
          }
          Object[] row = (Object[]) docs.get(i);
          BigInteger recordOrThumbnailId = (BigInteger) row[0];
          String originalFileName = (String) row[1];
          BigInteger blobId = (BigInteger) row[2];
          byte[] data = (byte[]) row[3];
          String thumbnailFname = pngIfyFileExt(originalFileName);
          Long blobIdL = blobId.longValue();
          // reuse existing FP if there is one
          FileProperty fp =
              blobIdToFileProperty.computeIfAbsent(
                  blobIdL,
                  id -> {
                    try (InputStream is = new ByteArrayInputStream(data)) {
                      return fStore.createAndSaveFileProperty(fsCategory, user, thumbnailFname, is);
                    } catch (IOException e) {
                      logger.error(
                          "{} thumbnail was not transferred -  continuing", recordOrThumbnailId);
                      return null;
                    }
                  });
          if (fp == null) {
            logger.error("could not create FP for {}", recordOrThumbnailId);
            continue;
          }

          int rowsModified =
              sessionFactory
                  .getCurrentSession()
                  .createSQLQuery(updateSQL)
                  .setParameter("fpid", fp.getId())
                  .setParameter("id", recordOrThumbnailId.longValue())
                  .setParameter("blobId", blobId.longValue())
                  .executeUpdate();
          if (rowsModified != 1) {
            logger.warn("num rows updated for docId {} -  {}", recordOrThumbnailId, rowsModified);
          }

          if (data.length != Integer.parseInt(fp.getFileSize())) {
            logger.warn(
                "Possible incorrect transfer of doc thumbnail for doc {}", recordOrThumbnailId);
          }
        }
        logger.info("{} thumbnails transferred  for user {}", docs.size(), user.getUsername());
      } finally {
        getTxMger().commit(nestedTx);
      }
    }
  }
}
