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

  private DocumentShares.Share toShare(BaseRecord record, RecordGroupSharing share) {
    boolean isUser = share.getSharee().isUser();
    BaseRecord location = locationResolver.resolveLocation(share, record);
    Long locationId = location != null ? location.getId() : null;
    String locationName = location != null ? location.getName() : null;

    return DocumentShares.Share.builder()
        .shareId(share.getId())
        .sharerId(share.getSharedBy().getId())
        .sharerName(share.getSharedBy().getDisplayName())
        .recipientName(share.getSharee().getDisplayName())
        .recipientType(
            isUser ? DocumentShares.RecipientType.USER : DocumentShares.RecipientType.GROUP)
        .permission(mapPermission(share.getPermType()))
        .locationId(locationId)
        .locationName(locationName)
        .build();
  }

  private static DocumentShares.PermissionType mapPermission(PermissionType perm) {
    return PermissionType.WRITE.equals(perm)
        ? DocumentShares.PermissionType.EDIT
        : DocumentShares.PermissionType.READ;
  }
}
