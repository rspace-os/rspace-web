package com.researchspace.service.impl;

import com.researchspace.dao.DBIntegrityDAO;
import com.researchspace.dao.FileMetadataDao;
import com.researchspace.model.FileStoreRoot;
import com.researchspace.model.record.BaseRecord;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.NonUniqueResultException;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;

/** Performs various checks on DB data integrity */
@Transactional
public class DBDataIntegrityChecker extends AbstractAppInitializor {

  Logger logger = LogManager.getLogger(DBDataIntegrityChecker.class);

  @Autowired SessionFactory sessionFactory;
  @Autowired private DBIntegrityDAO dao;
  @Autowired FileMetadataDao fileDao;

  @Override
  public void onAppStartup(ApplicationContext applicationContext) {
    assertNoTemporaryFavourites();
    assertAllRecordsHaveOwners();
    assertNotebookEntryOnlyHas1ParentNotebook();
    assertOnlyOneCurrentLocalFileStoreRoot();
  }

  private void assertOnlyOneCurrentLocalFileStoreRoot() {
    try {
      FileStoreRoot root = fileDao.getCurrentFileStoreRoot(false);
      if (root == null) {
        log.error("No current filestore root detected!");
      }
    } catch (NonUniqueResultException ex) {
      log.error("There is more than 1 local FileStoreRoot designated as 'current'");
    }
  }

  // every notebook entry can only belong to one notebook
  private void assertNotebookEntryOnlyHas1ParentNotebook() {
    List<Object> results =
        sessionFactory
            .getCurrentSession()
            .createSQLQuery(
                " select br.id, count(br.id) as numNotebookParents  from BaseRecord br inner join"
                    + " RecordToFolder rtf on rtf.record_id=br.id inner join BaseRecord parent on"
                    + " parent.id=rtf.folder_id where parent.type like '%notebook%' group by br.id"
                    + " having numNotebookParents > 1;")
            .list();
    if (!results.isEmpty()) {
      for (Object row : results) {
        Object[] rowData = (Object[]) row;
        String msg =
            String.format(
                "Notebook entry of  id [%s] has %s parent notebooks, but should only have 1!",
                rowData[0], rowData[1]);
        logger.warn(msg);
      }
    } else {
      logger.info("No notebook entries are in > 1 notebook, OK");
    }
  }

  private void assertAllRecordsHaveOwners() {
    try {
      List<BaseRecord> orphanedRecords = dao.getOrphanedRecords();
      if (!orphanedRecords.isEmpty()) {
        String orphanedRecordsString = StringUtils.join(orphanedRecords, ",");
        logger.warn("These Records don't have owners!:" + orphanedRecordsString);
      } else {
        logger.info("All records have owners, OK");
      }
    } catch (Exception e) {
      logDBQueryError(e);
    }
  }

  private void assertNoTemporaryFavourites() {
    try {
      List<Long> badFavs = dao.getTemporaryFavouriteDocs();
      if (!badFavs.isEmpty()) {
        String badFavsString = StringUtils.join(badFavs, ",");
        logger.warn(
            "These RecordUserFavorites are linked to temporary records, and should be deleted:"
                + badFavsString);
      } else {
        logger.info("No favourite temporary records, OK");
      }
    } catch (Exception e) {
      logDBQueryError(e);
    }
  }

  private void logDBQueryError(Exception e) {
    logger.error("Exception running diagnostic DB queries: " + e.getMessage());
  }
}
