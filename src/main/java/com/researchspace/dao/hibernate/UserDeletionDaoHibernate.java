package com.researchspace.dao.hibernate;

import static com.researchspace.core.util.TransformerUtils.toList;
import static java.lang.String.format;

import com.researchspace.dao.UserDao;
import com.researchspace.dao.UserDeletionDao;
import com.researchspace.model.User;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.UserDeletionPolicy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

@Repository
public class UserDeletionDaoHibernate implements UserDeletionDao {

  private static final String EXTRA_FIELD = "ExtraField";
  private static final String INVENTORY_FILE = "InventoryFile";
  private static final String BARCODE = "Barcode";
  private static final String DIGITAL_OBJECT_IDENTIFIER = "DigitalObjectIdentifier";
  private static final String RECORD_ID = "record_id";
  private static final String STRUCTURED_DOCUMENT_ID = "structuredDocument_id";
  private static final String COM_ID = "com_id";
  private static final String ECAT_COMM = "ecat_comm";
  private static final String FIELD = "Field";
  private @Autowired SessionFactory sessionFactory;
  private @Autowired UserDao userDao;

  private final UserDeletionQueryBuilder deletionQueryBuilder = new UserDeletionQueryBuilder();

  private final Logger log = LoggerFactory.getLogger(UserDeletionDaoHibernate.class);

  static final Map<String, String> table2UserIdColumn = new LinkedHashMap<>();

  private static final String USER_ID = "user_id";

  private static final String ORIGINATOR_ID = "originator_id";

  static {
    table2UserIdColumn.put("ArchivalCheckSum", "exporter_id");
    table2UserIdColumn.put("CommunicationTarget", "recipient_id");
    table2UserIdColumn.put("FormUsage", USER_ID);
    table2UserIdColumn.put("UserAccountEvent", USER_ID);
    table2UserIdColumn.put("FormUserMenu", USER_ID);
    table2UserIdColumn.put("UserPreference", USER_ID);
    table2UserIdColumn.put("UserApiKey", USER_ID);
    table2UserIdColumn.put("RecordUserFavorites", USER_ID);
    table2UserIdColumn.put("UserProfile", "owner_id");
    table2UserIdColumn.put("GroupMessageOrRequest", ORIGINATOR_ID);
    table2UserIdColumn.put("MessageOrRequest", ORIGINATOR_ID);
    table2UserIdColumn.put("Notification", ORIGINATOR_ID);
    table2UserIdColumn.put("OfflineRecordUser", USER_ID);
    table2UserIdColumn.put("NfsFileStore", USER_ID);
    table2UserIdColumn.put("user_role", USER_ID);
    table2UserIdColumn.put("TokenBasedVerification", USER_ID);
    table2UserIdColumn.put("ShareRecordMessageOrRequest", ORIGINATOR_ID);
    table2UserIdColumn.put("UserKeyPair", USER_ID);
    table2UserIdColumn.put("DMPUser", USER_ID);
  }

  static final String[] RecordTables1 =
      new String[] {
        "Notebook",
        "Folder",
        "EcatImage",
        "EcatDocumentFile",
        "EcatAudio",
        "EcatVideo",
        "EcatChemistryFile",
        "EcatMediaFile",
        "Snippet"
      };

  static final String[] RecordTables2 =
      new String[] {
        "StructuredDocument", "Record",
      };

  @Override
  public ServiceOperationResult<User> deleteUser(Long userId, UserDeletionPolicy policy) {
    if (policy.isTempUserOnlyDelete()) {
      return doDeleteTempUser(userId, policy);
    }
    if (policy.isForceDelete()) {
      return doForceDeleteUser(userId);
    }
    return new ServiceOperationResult<>(null, false, "Could not delete user, not yet enabled");
  }

  private ServiceOperationResult<User> doForceDeleteUser(Long userId) {
    Session session = sessionFactory.getCurrentSession();

    // delete user from group using group API
    User toDelete = userDao.get(userId);

    deleteSimpleTables(userId, session);
    deleteRecords(userId, session);
    deleteInventoryItems(userId, session);
    deleteGroups(userId, session);
    deleteForms(userId, session);
    deleteFileStoreContents(toDelete.getUsername(), session);
    deleteUserConnection(toDelete.getUsername(), session);
    deleteAppConfigs(userId, session);
    deleteOAuthTokens(userId, session);
    deleteOAuthApps(userId, session);
    deleteGroupMembershipEvent(userId, session);
    deleteExternalStorageLocation(userId, session);

    userDao.remove(userId);

    return new ServiceOperationResult<>(toDelete, true, "Temporary [" + userId + "] deleted");
  }

  private void deleteExternalStorageLocation(Long userId, Session session) {
    execute(userId, session, "delete from ExternalStorageLocation where operationUser_id=:id");
  }

  // this is called at the end, as userToDelete is removed from any groups in the same transaction,
  // which triggers
  // a log event in the DB, adding a row. If this is called in 'deleteSimpleTables' then user
  // deletion fails, complaining
  // about FK Reference.
  private void deleteGroupMembershipEvent(Long userId, Session session) {
    execute(userId, session, "delete from GroupMembershipEvent where user_id=:id");
  }

  private void deleteUserConnection(String username, Session session) {
    execute(username, session, "delete from UserConnection where userId=:id");
  }

  private void deleteFileStoreContents(String username, Session session) {
    deleteThumbnails(username, session);
    String queryStr = "delete from FileProperty where fileOwner = :uname";
    Query<?> query = session.createSQLQuery(queryStr);
    query.setParameter("uname", username);
    query.executeUpdate();
    session.flush();
  }

  private void deleteThumbnails(String username, Session session) {
    // Added delete to Thumbnail table to prevent foreign key constrain failure but this may
    // indicate a rethink is needed for the Thumbnail table
    String queryStr =
        "delete from Thumbnail where thumbnailFP_id in (select id from FileProperty where fileOwner"
            + " = :uname);";
    Query<?> query = session.createSQLQuery(queryStr);
    query.setParameter("uname", username);
    query.executeUpdate();
    session.flush();
  }

  private void deleteInventoryItems(Long userId, Session session) {

    /* start with baskets deletion */
    execute(
        userId,
        session,
        "delete bi from BasketItem bi left join Basket b on bi.basket_id = b.id where b.owner_id ="
            + " :id");
    execute(userId, session, "delete from Basket where owner_id = :id");

    /* start sample/subsample deletion */

    // first delete subsample-connected entities
    List<String> subSampleRelatedTables =
        toList(EXTRA_FIELD, INVENTORY_FILE, BARCODE, DIGITAL_OBJECT_IDENTIFIER, "SubSampleNote");
    for (String table : subSampleRelatedTables) {
      execute(
          userId,
          session,
          format(
              "delete ssRelTable from %s_AUD ssRelTable left join SubSample ss on"
                  + " ssRelTable.subSample_id = ss.id left join Sample s on ss.sample_id=s.id where"
                  + " s.owner_id = :id",
              table));
      execute(
          userId,
          session,
          format(
              "delete ssRelTable from %s ssRelTable left join SubSample ss on"
                  + " ssRelTable.subSample_id = ss.id left join Sample s on ss.sample_id=s.id where"
                  + " s.owner_id = :id",
              table));
    }

    // delete sample field connected entities
    List<String> sampleFieldRelatedTables = toList(INVENTORY_FILE);
    for (String table : sampleFieldRelatedTables) {
      execute(
          userId,
          session,
          format(
              "delete sfRelTable from %s_AUD sfRelTable left join SampleField sf on"
                  + " sfRelTable.sampleField_id = sf.id left join Sample s on sf.sample_id=s.id"
                  + " where s.owner_id = :id",
              table));
      execute(
          userId,
          session,
          format(
              "delete sfRelTable from %s sfRelTable left join SampleField sf on"
                  + " sfRelTable.sampleField_id = sf.id left join Sample s on sf.sample_id=s.id"
                  + " where s.owner_id = :id",
              table));
    }

    // set sample field FKs to null before deleting
    execute(
        userId,
        session,
        "update SampleField sf left join Sample s on sf.sample_id = s.id set templateField_id ="
            + " NULL where s.owner_id=:id");

    // delete sample-connected entities (including subsamples)
    List<String> sampleRelatedTables =
        toList(
            EXTRA_FIELD,
            INVENTORY_FILE,
            BARCODE,
            DIGITAL_OBJECT_IDENTIFIER,
            "SampleField",
            "SubSample");
    for (String table : sampleRelatedTables) {
      execute(
          userId,
          session,
          format(
              "delete sampTable from %s_AUD sampTable left join Sample s on"
                  + " sampTable.sample_id=s.id where s.owner_id = :id",
              table));
      execute(
          userId,
          session,
          format(
              "delete sampTable from %s sampTable left join Sample s on sampTable.sample_id=s.id"
                  + " where s.owner_id = :id",
              table));
    }

    // set sample template FKs to null before deleting samples
    execute(userId, session, "update Sample set STemplate_id = NULL where owner_id=:id");
    execute(userId, session, "update Sample_AUD set STemplate_id = NULL where owner_id=:id");
    execute(userId, session, "delete from Sample_AUD where owner_id = :id");
    execute(userId, session, "delete from Sample where owner_id = :id");

    /* start container deletion */

    // reset parent locations before removing locations, but just for containers as subsamples are
    // deleted at this point
    execute(userId, session, "update Container set parentLocation_id = NULL where owner_id = :id");
    execute(
        userId,
        session,
        "update Container set lastNonWorkbenchParent_id = NULL where owner_id = :id");
    execute(
        userId,
        session,
        "delete cl from ContainerLocation cl left join Container c on cl.container_id = c.id where"
            + " c.owner_id = :id");

    // delete container-connected entities
    List<String> containerRelatedTables =
        toList(EXTRA_FIELD, INVENTORY_FILE, BARCODE, DIGITAL_OBJECT_IDENTIFIER);
    for (String table : containerRelatedTables) {
      execute(
          userId,
          session,
          format(
              "delete contTable from %s_AUD contTable left join Container c on"
                  + " contTable.container_id = c.id where c.owner_id = :id",
              table));
      execute(
          userId,
          session,
          format(
              "delete contTable from %s contTable left join Container c on contTable.container_id ="
                  + " c.id where c.owner_id = :id",
              table));
    }

    execute(userId, session, "delete from Container_AUD where owner_id = :id");
    execute(userId, session, "delete from Container where owner_id = :id");
  }

  private void deleteAppConfigs(Long userId, Session session) {
    execute(
        userId,
        session,
        "delete from AppConfigElement where appConfigElementSet_id in "
            + "(select id from AppConfigElementSet where userAppConfig_id in "
            + "(select id from UserAppConfig where user_id=:id))");
    execute(
        userId,
        session,
        "delete from AppConfigElementSet where userAppConfig_id in "
            + "(select id from UserAppConfig where user_id=:id)");
    execute(userId, session, "delete from UserAppConfig where user_id=:id");
  }

  private void deleteOAuthTokens(Long userId, Session session) {
    execute(userId, session, "delete from OAuthToken where user_id = :id");
  }

  private void deleteOAuthApps(Long userId, Session session) {
    execute(userId, session, "delete from OAuthApp where user_id = :id");
  }

  // this will only work if user has no published forms!
  private void deleteForms(Long userId, Session session) {
    execute(
        userId,
        session,
        "delete FieldForm from FieldForm left join RSForm on FieldForm.form_id = RSForm.id where"
            + " RSForm.owner_id=:id");
    execute(
        userId,
        session,
        "delete FieldForm_AUD from FieldForm_AUD left join FieldForm on FieldForm_AUD.id ="
            + " FieldForm.id left join RSForm on FieldForm.form_id = RSForm.id where"
            + " RSForm.owner_id=:id");
    execute(userId, session, "delete from RSForm where owner_id=:id");
  }

  private void deleteGroups(Long userId, Session session) {
    execute(userId, session, "delete from UserGroup  where user_id = :id");
    execute(
        userId,
        session,
        "delete from CreateGroupMessageOrRequest_emails where CreateGroupMessageOrRequest_id in"
            + " (select id from CreateGroupMessageOrRequest where creator_id=:id)");
    execute(userId, session, "delete from CreateGroupMessageOrRequest  where creator_id=:id");
    deleteCreateGroupMessageWhereUserIsTarget(userId, session);

    execute(userId, session, "delete from RecordGroupSharing  where sharee_id=:id");
  }

  private void deleteCreateGroupMessageWhereUserIsTarget(Long userId, Session session) {
    execute(
        userId,
        session,
        "delete from CreateGroupMessageOrRequest_emails where CreateGroupMessageOrRequest_id in"
            + " (select id from CreateGroupMessageOrRequest where target_id=:id)");
    execute(userId, session, "delete from CreateGroupMessageOrRequest  where target_id=:id");
  }

  private void deleteRecords(Long userId, Session session) {

    execute(userId, session, "update User set rootFolder_id = NULL where id=:id");
    execute(userId, session, "update UserGroup set autoShareFolder_id = NULL where user_id=:id");
    execute(
        userId,
        session,
        "delete from SignatureHash where signature_id in (select id from Signature where"
            + " signer_id=:id)");
    execute(
        userId,
        session,
        "delete from Witness where signature_id in ( select id from Signature where"
            + " signer_id=:id)");
    execute(userId, session, "delete from Signature where signer_id=:id");

    executeDeleteByRecordOwner(userId, session, "RecordToFolder", "folder_id");
    executeDeleteByRecordOwner(userId, session, "RecordToFolder", RECORD_ID);
    executeDeleteByRecordOwner(userId, session, "RecordAttachment_AUD", RECORD_ID);
    executeDeleteByRecordOwner(
        userId,
        session,
        "FieldAttachment_AUD",
        "field_id",
        "Field_AUD",
        "id",
        STRUCTURED_DOCUMENT_ID);

    executeDeleteByRecordOwner(
        userId, session, "ecat_comm_item", COM_ID, ECAT_COMM, COM_ID, RECORD_ID);
    executeDeleteByRecordOwner(
        userId, session, "ecat_comm_item_AUD", COM_ID, ECAT_COMM, COM_ID, RECORD_ID);
    executeDeleteByRecordOwner(userId, session, ECAT_COMM, RECORD_ID);
    executeDeleteByRecordOwner(
        userId, session, "ecat_comm_AUD", COM_ID, ECAT_COMM, COM_ID, RECORD_ID);
    executeDeleteByRecordOwner(userId, session, "ecatImageAnnotation", RECORD_ID);
    executeDeleteByRecordOwner(
        userId, session, "ecatImageAnnotation_AUD", "id", "ecatImageAnnotation", "id", RECORD_ID);
    executeDeleteByRecordOwner(userId, session, "RSChemElement", RECORD_ID);
    executeDeleteByRecordOwner(
        userId, session, "RSChemElement_AUD", "id", "RSChemElement", "id", RECORD_ID);
    executeDeleteByRecordOwner(userId, session, "RSMath", RECORD_ID);
    executeDeleteByRecordOwner(userId, session, "RSMath_AUD", "id", "RSMath", "id", RECORD_ID);
    executeDeleteByRecordOwner(userId, session, "InternalLink", "source_id");
    executeDeleteByRecordOwner(userId, session, "InternalLink", "target_id");
    executeDeleteByRecordOwner(userId, session, "RecordUserFavorites", RECORD_ID);

    executeDeleteByRecordOwner(userId, session, "RecordAttachment", RECORD_ID);
    executeDeleteByRecordOwner(
        userId, session, "FieldAttachment", "field_id", FIELD, "id", STRUCTURED_DOCUMENT_ID);
    executeDeleteByRecordOwner(
        userId, session, "FieldAutosaveLog", "fieldId", FIELD, "id", STRUCTURED_DOCUMENT_ID);

    execute(
        userId,
        session,
        "update InventoryFile invf left join BaseRecord br on invf.mediaFile_id = br.id "
            + "set invf.mediaFile_id = NULL where br.owner_id=:id");
    execute(
        userId,
        session,
        "update EcatImage ei left join BaseRecord br on ei.id = br.id "
            + "set ei.originalImage_id = NULL where br.owner_id=:id");

    for (String recordTable1 : RecordTables1) {
      executeDeleteByRecordOwner(userId, session, recordTable1, "id");
      executeDeleteByRecordOwner(userId, session, recordTable1 + "_AUD", "id");
    }

    execute(
        userId,
        session,
        "delete mu from MaterialUsage_AUD mu left join ListOfMaterials lom on mu.parentLom_id ="
            + " lom.id left join Field f on lom.elnField_id = f.id left join BaseRecord br on"
            + " f.structuredDocument_id = br.id where br.owner_id=:id");
    execute(
        userId,
        session,
        "delete mu from MaterialUsage mu left join ListOfMaterials lom on mu.parentLom_id = lom.id"
            + " left join Field f on lom.elnField_id = f.id left join BaseRecord br on"
            + " f.structuredDocument_id = br.id where br.owner_id=:id");
    executeDeleteByRecordOwner(
        userId, session, "ListOfMaterials_AUD", "elnField_id", FIELD, "id", STRUCTURED_DOCUMENT_ID);
    executeDeleteByRecordOwner(
        userId, session, "ListOfMaterials", "elnField_id", FIELD, "id", STRUCTURED_DOCUMENT_ID);

    executeDeleteByRecordOwner(userId, session, FIELD, STRUCTURED_DOCUMENT_ID);
    executeDeleteByRecordOwner(userId, session, "Field_AUD", STRUCTURED_DOCUMENT_ID);

    for (String recordTable2 : RecordTables2) {
      executeDeleteByRecordOwner(userId, session, recordTable2, "id");
      executeDeleteByRecordOwner(userId, session, recordTable2 + "_AUD", "id");
    }

    executeDeleteByRecordOwner(userId, session, "RecordGroupSharing", "shared_id");
    executeDeleteByRecordOwner(userId, session, "Notification", RECORD_ID);

    execute(userId, session, "delete from BaseRecord where owner_id=:id");
    execute(userId, session, "delete from BaseRecord_AUD where owner_id=:id");
  }

  private void executeDeleteByRecordOwner(
      Long userId, Session session, String tableToDel, String tableToDelRecordIdColumn) {
    String deleteQuery =
        deletionQueryBuilder.generateDeleteByRecordOwnerQuery(tableToDel, tableToDelRecordIdColumn);
    execute(userId, session, deleteQuery);
  }

  private void executeDeleteByRecordOwner(
      Long userId,
      Session session,
      String tableToDel,
      String tableToDelJoinColumn,
      String joinTable,
      String joinTableJoinColumn,
      String joinTableRecordIdColumn) {

    String deleteQuery =
        deletionQueryBuilder.generateDeleteByRecordOwnerQuery(
            tableToDel,
            tableToDelJoinColumn,
            joinTable,
            joinTableJoinColumn,
            joinTableRecordIdColumn);
    execute(userId, session, deleteQuery);
  }

  private void deleteSimpleTables(Long userId, Session session) {
    for (Entry<String, String> entry : table2UserIdColumn.entrySet()) {
      session
          .createSQLQuery(
              "delete from " + entry.getKey() + " where " + entry.getValue() + " = :userId")
          .setParameter("userId", userId)
          .executeUpdate();
    }
    // there are 2 user ids in this table so we can't put them in the hashmap as we can't have 2
    // keys the same
    deleteShareRecordRequestWhereUserIsTarget(userId, session);
  }

  private void deleteShareRecordRequestWhereUserIsTarget(Long userId, Session session) {
    session
        .createSQLQuery("delete from ShareRecordMessageOrRequest where target_id = :userId")
        .setParameter("userId", userId)
        .executeUpdate();
  }

  private void execute(Object userId, Session session, String idQuery) {
    Query<?> query = session.createSQLQuery(idQuery);
    query.setParameter("id", userId);
    query.executeUpdate();
    session.flush();
  }

  private ServiceOperationResult<User> doDeleteTempUser(Long userId, UserDeletionPolicy policy) {
    User toDelete = userDao.get(userId);
    if (!toDelete.isTempAccount()) {
      return new ServiceOperationResult<>(
          toDelete, false, "User [" + userId + "] is not temporary");
    }
    try {
      Session session = sessionFactory.getCurrentSession();
      execute(userId, session, "delete from CommunicationTarget where recipient_id=:id");
      execute(userId, session, "delete from TokenBasedVerification where user_id=:id");
      deleteShareRecordRequestWhereUserIsTarget(userId, session);
      deleteCreateGroupMessageWhereUserIsTarget(userId, session);
      userDao.remove(userId);
      return new ServiceOperationResult<>(toDelete, true, "Temporary [" + userId + "] deleted");
    } catch (DataAccessException dae) {
      log.error("Could not delete user : {}", dae.getMessage());
      if (policy.isDisableAccountIfCannotBeDeleted()) {
        toDelete.setEnabled(false);
        userDao.save(toDelete);
        return new ServiceOperationResult<>(
            toDelete, false, "Could not delete temporary user, but account was disabled instead");
      } else {
        return new ServiceOperationResult<>(toDelete, false, "Could not delete temporary user");
      }
    }
  }
}
