package com.researchspace.service.mapping;

import com.researchspace.api.v1.model.DocumentShares;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.RecordInfoSharingInfo;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DocumentSharesBuilder {
  private final ShareLocationResolver locationResolver;

  @Autowired
  public DocumentSharesBuilder(ShareLocationResolver locationResolver) {
    this.locationResolver = locationResolver;
  }

  public DocumentShares assemble(BaseRecord record, RecordInfoSharingInfo sharingInfo) {
    return DocumentShares.builder()
        .sharedDocId(record.getId())
        .sharedDocName(record.getName())
        .directShares(mapShares(record, sharingInfo.getDirectShares()))
        .notebookShares(mapShares(record, sharingInfo.getImplicitShares()))
        .build();
  }

  private List<DocumentShares.Share> mapShares(BaseRecord record, List<RecordGroupSharing> shares) {
    return shares.stream().map(share -> toShare(record, share)).collect(Collectors.toList());
  }

  /***
   * Accepts both the BaseRecord and the RecordGroupSharing, despite RecordGroupSharing also containing the BaseRecord,
   * as in the case of a notebook entry indirectly shared via being part of a shared notebook, the BaseRecord will be a
   * notebook entry, whereas the RecordGroupSharing will refer to the notebook itself.
   */
  private DocumentShares.Share toShare(BaseRecord record, RecordGroupSharing share) {
    boolean isUser = share.getSharee().isUser();
    BaseRecord location = locationResolver.resolveLocation(share, record);
    Long locationId = location != null ? location.getId() : null;
    String path = locationResolver.resolvePath(share, record);

    return DocumentShares.Share.builder()
        .shareId(share.getId())
        .sharerId(share.getSharedBy().getId())
        .sharerName(share.getSharedBy().getDisplayName())
        .recipientId(share.getSharee().getId())
        .recipientName(share.getSharee().getDisplayName())
        .recipientType(
            isUser ? DocumentShares.RecipientType.USER : DocumentShares.RecipientType.GROUP)
        .permission(mapPermission(share.getPermType()))
        .locationId(locationId)
        .path(path)
        .build();
  }

  private static DocumentShares.PermissionType mapPermission(PermissionType perm) {
    return PermissionType.WRITE.equals(perm)
        ? DocumentShares.PermissionType.EDIT
        : DocumentShares.PermissionType.READ;
  }
}
