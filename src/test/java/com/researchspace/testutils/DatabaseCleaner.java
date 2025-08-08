package com.researchspace.testutils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Tidies up after transactional tests */
@Component
public class DatabaseCleaner {

  public static JdbcTemplate jdbcTemplate;

  @Autowired
  public void setDataSource(DataSource dataSource) {
    jdbcTemplate = new JdbcTemplate(dataSource);
  }

  /** Manually deletes records and folders from tables. */
  public static void cleanUp() {
    // delete from Batch tables
    deleteFromBatchTables();
    // remove FK relationships so that items can be deleted from DB
    jdbcTemplate.update("update UserPreference set user_id = NULL");
    jdbcTemplate.update("update RSForm set previousVersion_id = NULL");
    jdbcTemplate.update("update RSForm set tempForm_id = NULL");

    jdbcTemplate.update("update User set rootFolder_id = NULL");

    jdbcTemplate.update("update Notification set record_id = NULL");
    jdbcTemplate.update("update EcatMediaFile set fileProperty_id = NULL");
    jdbcTemplate.update("update EcatImage set originalImage_id = NULL");

    jdbcTemplate.update("update MessageOrRequest set record_id = NULL");
    jdbcTemplate.update("update MessageOrRequest set previous_id = NULL");
    jdbcTemplate.update("update MessageOrRequest set next_id = NULL");

    jdbcTemplate.update("update StructuredDocument set template_id = NULL");
    jdbcTemplate.update("update UserGroup set autoShareFolder_id = NULL");

    // simpleJdbcTemplate.update("update CommunicationTarget set communication_id = NULL");
    jdbcTemplate.update("delete from FieldAutosaveLog");
    jdbcTemplate.update("delete from UserAccountEvent");
    jdbcTemplate.update("delete from GroupMembershipEvent");
    jdbcTemplate.update("delete from UserConnection");
    jdbcTemplate.update("delete from RecordGroupSharing");
    jdbcTemplate.update("delete from RecordUserFavorites");
    jdbcTemplate.update("delete from CollabGroupCreationTracker");
    jdbcTemplate.update("delete from Witness");
    jdbcTemplate.update("delete from SignatureHash");
    jdbcTemplate.update("delete from Signature");
    jdbcTemplate.update("delete from UserProfile");
    jdbcTemplate.update("delete from Thumbnail");
    jdbcTemplate.update("delete from community_labGroups");
    jdbcTemplate.update("delete from community_admin");
    jdbcTemplate.update("delete from OfflineRecordUser");
    jdbcTemplate.update("delete from WhiteListedSysAdminIPAddress");

    jdbcTemplate.update("delete from ExternalStorageLocation");
    jdbcTemplate.update("delete from NfsFileStore");
    jdbcTemplate.update("delete from NfsFileSystem");
    jdbcTemplate.update("delete from UserKeyPair");
    jdbcTemplate.update("delete from UserConnection");
    jdbcTemplate.update("delete from IconEntity");
    jdbcTemplate.update("delete from AppConfigElement");
    jdbcTemplate.update("delete from AppConfigElementSet");
    jdbcTemplate.update("delete from UserAppConfig");
    jdbcTemplate.update("delete from TokenBasedVerification");
    jdbcTemplate.update("delete from ShareRecordMessageOrRequest");
    jdbcTemplate.update("delete from CommunicationTarget");
    jdbcTemplate.update("delete from MessageOrRequest");
    jdbcTemplate.update("delete from Notification");
    jdbcTemplate.update("delete from RecordToFolder");
    jdbcTemplate.update("delete from InternalLink");
    jdbcTemplate.update("delete from OAuthToken");
    jdbcTemplate.update("delete from DMPUser");
    jdbcTemplate.update("delete from ClustermarketBookings");
    jdbcTemplate.update("delete from ClustermarketEquipment");

    jdbcTemplate.update("delete from OAuthApp where id > 0");

    jdbcTemplate.update("update Container set parentLocation_id = NULL");
    jdbcTemplate.update("update Container set lastNonWorkbenchParent_id = NULL");
    jdbcTemplate.update("update SubSample set parentLocation_id = NULL");
    jdbcTemplate.update("update SubSample set lastNonWorkbenchParent_id = NULL");
    jdbcTemplate.update("delete from ContainerLocation");

    jdbcTemplate.update("update SampleField set choiceDef_id = NULL");
    jdbcTemplate.update("update SampleField set radioDef_id = NULL");
    jdbcTemplate.update("update SampleField set templateField_id = NULL");
    jdbcTemplate.update("update Sample set STemplate_id = NULL");

    jdbcTemplate.update("delete from BasketItem");
    jdbcTemplate.update("delete from Basket");

    jdbcTemplate.update("delete from StoichiometryMolecule");
    jdbcTemplate.update("delete from Stoichiometry");

    // Add to this list if more tables are used in the tests. Order is
    // important to avoid referential integrity problems.
    // This is a list of tables that are audited
    List<String> toDelete =
        Arrays.asList(
            "RecordAttachment",
            "FieldAttachment",
            "RSMath",
            "MaterialUsage",
            "ListOfMaterials",
            "InventoryFile",
            "Field",
            "EcatImage",
            "Snippet",
            "EcatAudio",
            "EcatVideo",
            "EcatDocumentFile",
            "RSChemElement",
            "EcatChemistryFile",
            "EcatMediaFile",
            "StructuredDocument",
            "ecat_comm_item",
            "ecat_comm",
            "ecatImageAnnotation",
            "Record",
            "Barcode",
            "DigitalObjectIdentifier",
            "ExtraField",
            "SampleField",
            "SubSampleNote",
            "SubSample",
            "Sample",
            "Container",
            "FieldForm",
            "RSForm",
            "Notebook",
            "Folder",
            "BaseRecord");

    // adds _AUD auditing tables to be wiped clean as well.
    List<String> toDeleteAUD = new ArrayList<String>();
    for (String table : toDelete) {
      String aud = table + "_AUD";
      toDeleteAUD.add(aud);
    }
    toDeleteAUD.addAll(toDelete);
    for (String tablename : toDeleteAUD) {
      jdbcTemplate.update("delete from " + tablename);
    }

    jdbcTemplate.update("delete from FileProperty");
    jdbcTemplate.update("delete from ImageBlob");

    jdbcTemplate.update("delete from EcatComment_EcatCommentItem_AUD");
    jdbcTemplate.update("delete from CreateGroupMessageOrRequest_emails");
    jdbcTemplate.update("delete from CreateGroupMessageOrRequest");
    jdbcTemplate.update("delete from GroupMessageOrRequest");
    // delete tables that don't have corresponding _AUD tables
    jdbcTemplate.update("delete from UserGroup");
    jdbcTemplate.update("delete from rsGroup");
    jdbcTemplate.update("delete from FormUsage");
    jdbcTemplate.update("delete from FormUserMenu");
    jdbcTemplate.update("delete from UserPreference");
    jdbcTemplate.update("delete from UserApiKey");
    jdbcTemplate.update("delete from ArchivalCheckSum");
    jdbcTemplate.update("delete from InventoryChoiceFieldDef");
    jdbcTemplate.update("delete from InventoryRadioFieldDef");

    // remove any users we've added during the tests
    jdbcTemplate.update("delete from user_role where user_id > 0");
    jdbcTemplate.update("delete from User where id > 0");
    // keep default community (-1) between tests
    jdbcTemplate.update("delete from SystemPropertyValue where community_id is not NULL");
    jdbcTemplate.update("delete from Community where id >= 0");
    jdbcTemplate.update("delete from ScheduledMaintenance");

    // this has to be the last table to be removed
    jdbcTemplate.update("delete from REVINFO");
  }

  private static void deleteFromBatchTables() {
    // table ordering derived from drop-table-mysql in spring batch core jar package o.s.batch.core
    jdbcTemplate.update("delete from BATCH_STEP_EXECUTION_CONTEXT");
    jdbcTemplate.update("delete from BATCH_JOB_EXECUTION_CONTEXT");
    jdbcTemplate.update("delete from BATCH_STEP_EXECUTION ");
    jdbcTemplate.update("delete from BATCH_JOB_EXECUTION_PARAMS");
    jdbcTemplate.update("delete from BATCH_JOB_EXECUTION");
    jdbcTemplate.update("delete from BATCH_JOB_INSTANCE ");
  }
}
