package com.researchspace.service;

import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.DocumentInitializationPolicy;
import java.util.List;
import java.util.Map;
import org.apache.shiro.authz.AuthorizationException;

/** A manager serving BaseRecords. It delegates the work to either Record or Folder manager. */
public interface BaseRecordManager {

  /**
   * Saves or updates the BaseRecord
   *
   * @param BaseRecord
   * @param user
   * @return the persisted or updated BaseRecord
   */
  BaseRecord save(BaseRecord record, User user);

  /**
   * Gets a base record by Id. <br>
   * Throws exception ObjectRetrievalFailureException if id is not found, or AuthorizationException
   * if id is a Folder that can't be read by the user (e.g. because is deleted).
   *
   * @param recordId
   * @return A {@link BaseRecord} or exception
   */
  BaseRecord get(Long recordId, User user);

  /**
   * As get((Long recordId, User user) but will retrieve a folder object even if the folder is
   * deleted.
   *
   * @param recordId
   * @param user
   * @param includedDeletedFolder
   * @return
   */
  BaseRecord get(Long recordId, User user, boolean includedDeletedFolder);

  /**
   * Loads up a proxied object for forming associations
   *
   * @param id
   * @return
   */
  BaseRecord load(Long id);

  /**
   * Facade method to retrieve media files, a current one or for particular revision. <br>
   * Also asserts read authorisation. Return map Values are null if noyt authorised
   *
   * @param mediaFileId
   * @param revisionId
   * @param policy (optional) policy to use for populating non-audited record, if not provided uses
   *     LinkedFieldsToMediaRecordInitPolicy()
   * @return Map<Long, EcatMediaFile> where keys are mediaFile IDS
   */
  Map<String, EcatMediaFile> retrieveMediaFiles(
      User subject, Long[] mediaFileId, Long[] revisionId, DocumentInitializationPolicy policy);

  /**
   * Facade method to retrieve a media files, a current one or for particular revision/version. <br>
   * Also asserts read authorisation.
   *
   * @param subject
   * @param mediaFileId
   * @param revisionId (not required) envers revision number to retrieve
   * @param version (not required) media file version number to retrieve
   * @param policy (not required) policy to use for populating non-audited record, if not provided
   *     uses LinkedFieldsToMediaRecordInitPolicy()
   * @return EcatMediaFile
   * @throws AuthorizationException if not permitted
   */
  EcatMediaFile retrieveMediaFile(
      User subject,
      Long mediaFileId,
      Long revisionId,
      Long version,
      DocumentInitializationPolicy policy);

  /**
   * Facade method to retrieve a media files, a current one or for the current last
   * revision/version. Using LinkedFieldsToMediaRecordInitPolicy to populate the non-audited records
   * <br>
   * Also asserts read authorisation.
   *
   * @param user
   * @param mediaFileId
   * @return EcatMediaFile
   * @throws AuthorizationException if not permitted
   */
  EcatMediaFile retrieveMediaFile(User user, Long mediaFileId);

  /**
   * Given a list of global identifiers (assumed to be those of BaseRecords) will load up the actual
   * BaseRecords, returning those permitted to be viewed by the subject
   *
   * @param baseRecordIds
   * @param subject
   * @return
   */
  List<BaseRecord> getByIdAndReadPermission(List<GlobalIdentifier> baseRecordIds, User subject);
}
