package com.researchspace.service.impl;

import com.researchspace.dao.FieldDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.dao.RecordGroupSharingDao;
import com.researchspace.model.AbstractUserOrGroupImpl;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.FieldAttachment;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.Record;
import com.researchspace.service.FieldManager;
import java.util.List;
import java.util.Optional;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("fieldManager")
public class FieldManagerImpl implements FieldManager {

  protected final Logger log = LoggerFactory.getLogger(getClass());

  private FieldDao fieldDao;

  private @Autowired RecordDao recordDao;
  private @Autowired IPermissionUtils permUtils;
  private @Autowired RecordGroupSharingDao recordSharingDao;

  @Autowired
  public void setUserDao(FieldDao fieldDao) {
    this.fieldDao = fieldDao;
  }

  public Optional<Field> get(Long id, User user) {
    return fieldDao.getSafeNull(id);
  }

  public Field save(Field field, User user) {
    return fieldDao.save(field);
  }

  public void delete(Long id, User user) {
    // XXX: Need permissions system
    fieldDao.remove(id);
  }

  @Override
  public List<Field> getFieldsByRecordId(long id, User user) {
    List<Field> listFields = fieldDao.getFieldFromStructuredDocument(id);
    return listFields;
  }

  @Override
  public List<Field> getFieldsByRecordIdFromColumnNumber(
      long recordId, int columnNumber, User user) {
    List<Field> listFields = fieldDao.getFieldByRecordIdFromColumnNumber(recordId, columnNumber);
    return listFields;
  }

  @Override
  public List<String> getFieldNamesForRecord(Long recordId) {
    return fieldDao.getFieldNamesForRecord(recordId);
  }

  @Override
  public List<Long> getFieldIdsForRecord(Long recordId) {
    return fieldDao.getFieldIdsForRecord(recordId);
  }

  public Optional<FieldAttachment> addMediaFileLink(
      Long ecatMediaFileId, User subject, Long fieldId, Boolean ignorePermissions) {
    Record mediaFile = recordDao.get(ecatMediaFileId);
    if (!mediaFile.isMediaRecord()) {
      throw new IllegalArgumentException("Can't add non media-file link to field");
    }
    // do we have read permission on the media file id(see RSTEST-165)
    if (!ignorePermissions && !subjectCanReadMediaFile(subject, mediaFile)) {
      // if not, we probably want to continue wit hthe rest of the save
      log.error(createAuthExceptionMsg(subject, fieldId, mediaFile));
      return Optional.empty();
    }
    Field field = get(fieldId, subject).get();
    // can we edit the document by adding an attachment link to it?
    if (!ignorePermissions
        && !permUtils.isPermitted(field.getStructuredDocument(), PermissionType.WRITE, subject)) {
      throwAuthException(subject, fieldId, mediaFile);
    }

    Optional<FieldAttachment> fldAtachment = field.addMediaFileLink((EcatMediaFile) mediaFile);
    save(field, subject);
    // rspac-930
    if (field.getStructuredDocument().isTemplate()) {
      List<AbstractUserOrGroupImpl> usersOrGroups =
          recordSharingDao.getUsersOrGroupsWithRecordAccess(field.getStructuredDocument().getId());
      for (AbstractUserOrGroupImpl userOrGroup : usersOrGroups) {
        mediaFile
            .getSharingACL()
            .addACLElement(
                userOrGroup,
                new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.READ));
      }
      recordDao.save(mediaFile);
    }
    return fldAtachment;
  }

  public boolean subjectCanReadMediaFile(User subject, Record mediaFile) {
    return permUtils.isPermitted(mediaFile, PermissionType.READ, subject)
        || permUtils.isPermittedViaMediaLinksToRecords(mediaFile, PermissionType.READ, subject);
  }

  private void throwAuthException(User subject, Long fieldId, Record mediaFile) {
    String msg = createAuthExceptionMsg(subject, fieldId, mediaFile);
    throw new AuthorizationException(msg);
  }

  public String createAuthExceptionMsg(User subject, Long fieldId, Record mediaFile) {
    String msg =
        String.format(
            "Unauthorised attempt by  %s  to link media file [%s] to file [%s]",
            subject.getUsername(), mediaFile.getId(), fieldId);
    return msg;
  }

  @Override
  public EcatMediaFile removeMediaFileLink(Long ecatMediaFileId, User subject, Long fieldId) {
    Record mediaFile = recordDao.get(ecatMediaFileId);
    if (!mediaFile.isMediaRecord()) {
      throw new IllegalArgumentException("Can't remove non media-file link from field");
    }
    Field field = get(fieldId, subject).get();
    field.removeMediaFileLink((EcatMediaFile) mediaFile);
    save(field, subject);
    return (EcatMediaFile) mediaFile;
  }

  @Override
  public List<Field> findByTextContent(String text) {
    return fieldDao.findByTextContent(text);
  }

  public Optional<Field> getWithLoadedMediaLinks(Long id, User user) {
    Optional<Field> field = get(id, user);
    field.get().getLinkedMediaFiles().size();
    return field;
  }
}
