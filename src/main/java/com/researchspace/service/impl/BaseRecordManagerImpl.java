package com.researchspace.service.impl;

import com.researchspace.dao.FolderDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.DocumentInitializationPolicy;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.LinkedFieldsToMediaRecordInitPolicy;
import com.researchspace.model.record.Record;
import com.researchspace.service.AuditManager;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.FolderManager;
import com.researchspace.service.OperationFailedMessageGenerator;
import com.researchspace.service.RecordManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.stereotype.Service;

/**
 * It's a facade manager that is delegating to either Record or Folder manager depending on object
 * (or identifier) passed as an argument.
 */
@Service("baseRecordManager")
public class BaseRecordManagerImpl implements BaseRecordManager {

  private @Autowired RecordManager recordManager;
  private @Autowired FolderManager folderManager;
  private @Autowired FolderDao folderDao;
  private @Autowired RecordDao recordDao;
  private @Autowired AuditManager auditManager;
  private @Autowired OperationFailedMessageGenerator authGenerator;
  private @Autowired IPermissionUtils permissionUtils;

  public BaseRecord save(BaseRecord baseRecord, User user) {
    if (isRecord(baseRecord)) {
      return recordManager.save((Record) baseRecord, user);
    }
    return folderManager.save((Folder) baseRecord, user);
  }

  @Override
  public BaseRecord get(Long recordId, User user) {
    return get(recordId, user, false);
  }

  @Override
  public BaseRecord get(Long recordId, User user, boolean includedDeletedFolder) {
    if (isRecord(recordId)) {
      return recordManager.get(recordId);
    }
    return folderManager.getFolder(recordId, user, includedDeletedFolder);
  }

  protected boolean isRecord(BaseRecord baseRecord) {
    return baseRecord instanceof Record;
  }

  protected boolean isRecord(Long id) {
    return recordManager.exists(id);
  }

  @Override
  public BaseRecord load(Long id) {
    if (isRecord(id)) {
      return recordDao.load(id);
    }
    return folderDao.load(id);
  }

  @Override
  public Map<String, EcatMediaFile> retrieveMediaFiles(
      User subject, Long[] mediaFileId, Long[] revisionId, DocumentInitializationPolicy policy) {
    Map<String, EcatMediaFile> mediaFiles = new HashMap<>();
    for (int i = 0; i < mediaFileId.length; i++) {
      Long id = mediaFileId[i];
      Long revision = revisionId[i];
      try {
        EcatMediaFile emf = retrieveMediaFile(subject, id, revision, null, null);
        mediaFiles.put(id + "-" + revision, emf);
      } catch (AuthorizationException | IllegalStateException | DataAccessException e) {
        mediaFiles.put(id + "-" + revision, null);
      }
    }
    return mediaFiles;
  }

  @Override
  public List<BaseRecord> getByIdAndReadPermission(
      List<GlobalIdentifier> baseRecordIds, User subject) {
    List<BaseRecord> brs = new ArrayList<BaseRecord>();
    for (GlobalIdentifier gId : baseRecordIds) {
      BaseRecord br = get(gId.getDbId(), subject, false);
      brs.add(br);
    }
    return permissionUtils.filter(brs, PermissionType.READ, subject);
  }

  @Override
  public EcatMediaFile retrieveMediaFile(User subject, Long mediaFileId) {
    return retrieveMediaFile(subject, mediaFileId, null, null, null);
  }

  @Override
  public EcatMediaFile retrieveMediaFile(
      User subject,
      Long mediaFileId,
      Long revisionId,
      Long version,
      DocumentInitializationPolicy policy) {
    if (policy == null) {
      policy = new LinkedFieldsToMediaRecordInitPolicy();
    }
    EcatMediaFile ecatMediaFile =
        (EcatMediaFile)
            recordManager
                .getOptRecordWithLazyLoadedProperties(mediaFileId, subject, policy, true)
                .orElseThrow(() -> new ObjectRetrievalFailureException(Record.class, mediaFileId));

    permissionUtils.assertRecordAccessPermitted(
        ecatMediaFile,
        PermissionType.READ,
        subject,
        authGenerator.getFailedMessage(subject, "read media file"));

    Long revisionToUse = revisionId;
    if (version != null && ecatMediaFile.getVersion() != version) {
      revisionToUse =
          auditManager.getRevisionNumberForMediaFileVersion(mediaFileId, version).longValue();
    }

    if (revisionToUse != null) {
      AuditedEntity<EcatMediaFile> audited =
          auditManager.getRevisionForMediaFile(ecatMediaFile, revisionToUse);
      if (audited != null) {
        ecatMediaFile = audited.getEntity();
      } else {
        throw new IllegalStateException("couldn't retrieve revision of media file");
      }
    }
    return ecatMediaFile;
  }
}
