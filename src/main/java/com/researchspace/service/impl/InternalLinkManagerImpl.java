package com.researchspace.service.impl;

import com.researchspace.dao.FieldDao;
import com.researchspace.dao.InternalLinkDao;
import com.researchspace.model.InternalLink;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.InternalLinkManager;
import java.util.List;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("internalLinkManager")
public class InternalLinkManagerImpl implements InternalLinkManager {

  private @Autowired InternalLinkDao internalLinkDao;
  private @Autowired IPermissionUtils permissionUtils;
  private @Autowired FieldDao fieldDao;
  private @Autowired BaseRecordManager baseRecordMgr;

  @Override
  public List<InternalLink> getLinksPointingToRecord(long targetRecordId) {
    return internalLinkDao.getLinksPointingToRecord(targetRecordId);
  }

  @Override
  public boolean createInternalLink(Long srcFieldId, Long linkedRecordId, User subject) {
    BaseRecord targetRecord = baseRecordMgr.get(linkedRecordId, subject);
    Validate.isTrue(
        isValidLinkTarget(targetRecord), "Linked item must be a folder, notebook or document");
    permissionUtils.assertIsPermitted(
        targetRecord, PermissionType.READ, subject, "create internal link");
    Field srcField = fieldDao.get(srcFieldId);
    Validate.isTrue(
        srcField.isTextField(), "Field that will contain the link must be a text field");
    permissionUtils.assertIsPermitted(
        srcField.getStructuredDocument(), PermissionType.WRITE, subject, "create internal link");
    return internalLinkDao.saveInternalLink(
        srcField.getStructuredDocument().getId(), linkedRecordId);
  }

  private boolean isValidLinkTarget(BaseRecord targetRecord) {
    return (targetRecord.isNotebook()
            || targetRecord.isFolder()
            || targetRecord.isStructuredDocument())
        && !targetRecord.isDeleted();
  }
}
