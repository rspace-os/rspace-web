package com.researchspace.service.impl;

import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.EditStatus;
import com.researchspace.model.InternalLink;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.SignatureStatus;
import com.researchspace.model.User;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Breadcrumb;
import com.researchspace.model.record.BreadcrumbGenerator;
import com.researchspace.model.record.DefaultBreadcrumbGenerator;
import com.researchspace.model.record.DetailedRecordInformation;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RecordInfoSharingInfo;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.AuditManager;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.DetailedRecordInformationProvider;
import com.researchspace.service.FolderManager;
import com.researchspace.service.InternalLinkManager;
import com.researchspace.service.OperationFailedMessageGenerator;
import com.researchspace.service.RecordManager;
import com.researchspace.service.RecordSharingManager;
import com.researchspace.service.RecordSigningManager;
import com.researchspace.session.UserSessionTracker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;

public class DetailedRecordInformationProviderImpl implements DetailedRecordInformationProvider {

  private @Autowired RecordSigningManager signingManager;
  private @Autowired BaseRecordManager baseRecordManager;
  private @Autowired InternalLinkManager internalLinkManager;
  private @Autowired RecordManager recordManager;
  private @Autowired FolderManager folderManager;
  private @Autowired RecordEditorTracker tracker;
  private @Autowired IPermissionUtils permissionUtils;
  private @Autowired OperationFailedMessageGenerator authGenerator;
  private @Autowired RecordSharingManager sharingManager;
  private @Autowired AuditManager auditMgr;

  private BreadcrumbGenerator breadcrumbGenerator = new DefaultBreadcrumbGenerator();

  @Override
  public DetailedRecordInformation getDetailedRecordInformation(
      Long recordId,
      UserSessionTracker userSessionTracker,
      User subject,
      Long revision,
      Long userVersion) {

    BaseRecord baseRecord = baseRecordManager.get(recordId, subject);

    permissionUtils.assertRecordAccessPermitted(
        baseRecord,
        PermissionType.READ,
        subject,
        authGenerator.getFailedMessage(subject, "read record"));

    boolean isMediaRevisionInfo = (revision != null && baseRecord.isMediaRecord());
    if (isMediaRevisionInfo) {
      baseRecord = baseRecordManager.retrieveMediaFile(subject, recordId, revision, null, null);
    }
    boolean isDocVersionInfo = (userVersion != null && baseRecord.isStructuredDocument());
    if (isDocVersionInfo) {
      baseRecord =
          auditMgr
              .getDocumentRevisionOrVersion(baseRecord.asStrucDoc(), null, userVersion)
              .getRecord();
    }

    DetailedRecordInformation detailedInfo = new DetailedRecordInformation(baseRecord);
    if (isDocVersionInfo) {
      detailedInfo.setOid(baseRecord.asStrucDoc().getOidWithVersion());
    }
    if (detailedInfo.getOriginalImageOid() != null) {
      // maybe versioned oid is unnecessary, if latest points to the same version
      BaseRecord originalImage =
          baseRecordManager.get(detailedInfo.getOriginalImageOid().getDbId(), subject);
      if (originalImage.isIdentifiedByOid(detailedInfo.getOriginalImageOid())) {
        detailedInfo.setOriginalImageOid(originalImage.getOid());
      }
    }

    addSharingInfo(recordId, baseRecord, detailedInfo);
    addStrucDocDetailedInfo(
        subject, baseRecord, detailedInfo, isDocVersionInfo, userSessionTracker);

    if (!isMediaRevisionInfo && !isDocVersionInfo) {
      if (baseRecord.isMediaRecord()
          && isRecordAccessPermitted(subject, baseRecord, PermissionType.WRITE)) {
        detailedInfo.setStatus(EditStatus.VIEW_MODE.toString());
      }

      List<InternalLink> linkedBy = internalLinkManager.getLinksPointingToRecord(recordId);
      if (linkedBy != null) {
        detailedInfo.setLinkedByCount(linkedBy.size());
      }
    }

    addBreadcrumbs(subject, baseRecord, detailedInfo);
    detailedInfo.setRevision(revision);
    return detailedInfo;
  }

  private void addBreadcrumbs(
      User subject, BaseRecord baseRecord, DetailedRecordInformation detailedInfo) {
    Breadcrumb breadcrumb = getBreadcrumbToHome(baseRecord, subject);
    detailedInfo.setPath(breadcrumb.getAsStringPath());
  }

  private Breadcrumb getBreadcrumbToHome(BaseRecord baseRecord, User subject) {
    return breadcrumbGenerator.generateBreadcrumbToHome(
        baseRecord, folderManager.getRootFolderForUser(subject), null);
  }

  @Override
  public void addSharingInfo(
      Long recordId, BaseRecord baseRecord, DetailedRecordInformation detailedInfo) {
    if (baseRecord.isStructuredDocument() || baseRecord.isNotebook()) {
      List<RecordGroupSharing> sharingsList = sharingManager.getRecordSharingInfo(recordId);
      sharingsList =
          sharingsList.stream()
              .filter(rgs -> rgs.getPublicLink() == null)
              .collect(Collectors.toList());
      List<RecordGroupSharing> implicitSharingList = new ArrayList<>();
      // add information about documents in shared notebooks, that aren't themselves shared,
      // but are viewable by other users
      if (baseRecord.isStructuredDocument()) {
        Set<Notebook> parentNbs = baseRecord.getParentNotebooks();
        for (Notebook n : parentNbs) {
          List<RecordGroupSharing> parentNbSharedInfo =
              sharingManager.getRecordSharingInfo(n.getId());
          if (!parentNbSharedInfo.isEmpty()) {
            implicitSharingList.addAll(parentNbSharedInfo);
          }
        }
      }
      implicitSharingList =
          implicitSharingList.stream()
              .filter(rgs -> rgs.getPublicLink() == null)
              .collect(Collectors.toList());
      detailedInfo.calculateSharedStatuses(
          new RecordInfoSharingInfo(sharingsList, implicitSharingList));
    }
  }

  private void addStrucDocDetailedInfo(
      User user,
      BaseRecord baseRecord,
      DetailedRecordInformation detailedInfo,
      boolean isDocVersionInfo,
      UserSessionTracker userSessionTracker) {
    if (baseRecord.isStructuredDocument()) {
      StructuredDocument sd = (StructuredDocument) baseRecord;

      // TODO: optimize - this is done because the template in the StructuredDocument
      // is Lazy loaded and we are out of the session right now
      if (sd.getTemplate() != null) {
        BaseRecord template = baseRecordManager.get(sd.getTemplate().getId(), user);
        detailedInfo.setTemplateName(template.getName());
        detailedInfo.setTemplateOid(template.getOid().toString());
      }

      if (isDocVersionInfo) {
        detailedInfo.setStatus(EditStatus.CAN_NEVER_EDIT.toString());
      } else {
        EditStatus status =
            recordManager.requestRecordView(baseRecord.getId(), user, userSessionTracker);
        detailedInfo.setStatus(status.toString());
        if (EditStatus.CANNOT_EDIT_OTHER_EDITING.equals(status)) {
          String editor = tracker.getEditingUserForRecord(baseRecord.getId());
          detailedInfo.setCurrentEditor(editor);
        }
      }

      if (detailedInfo.getSigned()) {
        detailedInfo.setSignatureStatus(
            signingManager.getSignatureForRecord(baseRecord.getId()).toSignatureInfo().getStatus());
      } else if (baseRecord.isNotebookEntry() || baseRecord.isStructuredDocument()) {
        detailedInfo.setSignatureStatus(SignatureStatus.UNSIGNED);
      } else {
        detailedInfo.setSignatureStatus(SignatureStatus.UNSIGNABLE);
      }
    }
  }

  protected boolean isRecordAccessPermitted(
      User user, BaseRecord objectToCheck, PermissionType permType) {
    return permissionUtils.isRecordAccessPermitted(user, objectToCheck, permType);
  }

  @Override
  public List<RecordInformation> getLinkedByRecords(Long targetRecordId, User subject) {
    List<RecordInformation> linkedByRecords = new ArrayList<>();
    List<InternalLink> linkedBy = internalLinkManager.getLinksPointingToRecord(targetRecordId);
    for (InternalLink link : linkedBy) {
      boolean accessPermitted =
          permissionUtils.isPermitted(link.getSource(), PermissionType.READ, subject);
      if (accessPermitted) {
        linkedByRecords.add(link.getSource().toRecordInfo());
      } else {
        RecordInformation ownersInfo = new RecordInformation();
        ownersInfo.setOwnerUsername(link.getSource().getOwner().getUsername());
        ownersInfo.setOwnerFullName(link.getSource().getOwner().getFullName());
        linkedByRecords.add(ownersInfo);
      }
    }
    return linkedByRecords;
  }

  @Override
  public Map<String, RecordInformation> getRecordInformation(
      Long[] ids, Long[] revisions, User subject) {
    Map<String, EcatMediaFile> res =
        baseRecordManager.retrieveMediaFiles(subject, ids, revisions, null);
    Map<String, RecordInformation> mediaInfos = new HashMap<>();
    for (Map.Entry<String, EcatMediaFile> entry : res.entrySet()) {
      // RA can't use streams as values may be null, which valuemapper will choke on
      mediaInfos.put(
          entry.getKey(), entry.getValue() != null ? entry.getValue().toRecordInfo() : null);
    }
    return mediaInfos;
  }
}
