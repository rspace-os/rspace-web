package com.researchspace.dao.customliquibaseupdates;

import com.researchspace.dao.RecordDao;
import com.researchspace.model.permissions.ACLElement;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.permissions.RecordSharingACL;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Record;
import java.util.List;
import liquibase.database.Database;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UpdateEmptyACLsForMediaFiles_RSPAC2559 extends AbstractCustomLiquibaseUpdater {

  private RecordDao recordDao;
  private int recordsUpdated = 0;

  @Override
  protected void addBeans() {
    recordDao = context.getBean(RecordDao.class);
  }

  @Override
  public String getConfirmationMessage() {
    return "Updated " + recordsUpdated + " records";
  }

  @Override
  protected void doExecute(Database database) {

    List<BaseRecord> records = getRecordsWithEmptyACLs();
    log.info("Found {} records with empty acls.", records.size());
    for (BaseRecord record : records) {
      RecordSharingACL acl = record.getSharingACL();
      // Only update empty acls and media records
      if (!acl.isACLPopulated() && record.isMediaRecord()) {
        String username = record.getCreatedBy();
        acl.addACLElement(
            new ACLElement(
                username,
                new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.WRITE)));
        acl.addACLElement(
            new ACLElement(
                username,
                new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.CREATE)));
        acl.addACLElement(
            new ACLElement(
                username,
                new ConstraintBasedPermission(
                    PermissionDomain.RECORD, PermissionType.CREATE_FOLDER)));
        acl.addACLElement(
            new ACLElement(
                username,
                new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.SEND)));
        acl.addACLElement(
            new ACLElement(
                username,
                new ConstraintBasedPermission(
                    PermissionDomain.RECORD, PermissionType.FOLDER_RECEIVE)));
        acl.addACLElement(
            new ACLElement(
                username,
                new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.RENAME)));
        acl.addACLElement(
            new ACLElement(
                username,
                new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.COPY)));
        log.info("Found record with empty acl: {} belonging to user {}", record.getId(), username);
        log.info("Adding acl {} to record {}", acl.getAcl(), record.getId());
        recordDao.save((Record) record);
        recordsUpdated++;
      }
    }
  }

  private List<BaseRecord> getRecordsWithEmptyACLs() {

    return sessionFactory
        .getCurrentSession()
        .createQuery("from BaseRecord where acl = '' and type = 'MEDIA_FILE'", BaseRecord.class)
        .list();
  }
}
