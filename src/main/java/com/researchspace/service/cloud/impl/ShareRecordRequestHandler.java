package com.researchspace.service.cloud.impl;

import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.ShareRecordAuditEvent;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.comms.ShareRecordMessageOrRequest;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.RecordSharingManager;
import com.researchspace.service.impl.AbstractRSpaceRequestUpdateHandler;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/** Handler for requests of type MessageType.REQUEST_SHARE_RECORD */
public class ShareRecordRequestHandler extends AbstractRSpaceRequestUpdateHandler {

  private Logger logger = LoggerFactory.getLogger(ShareRecordRequestHandler.class);

  private @Autowired RecordSharingManager sharingManager;
  private @Autowired IPermissionUtils permissnUtils;

  @Override
  public boolean handleRequest(MessageType messageType) {
    return MessageType.REQUEST_SHARE_RECORD.equals(messageType);
  }

  @Override
  protected void doHandleMessageOrRequestUpdateOnCompletion(
      CommunicationTarget updatedTarget, User subject) {

    MessageOrRequest mor = (MessageOrRequest) updatedTarget.getCommunication();
    if (!(mor instanceof ShareRecordMessageOrRequest)) {
      return;
    }

    logger.debug(
        "Handling request updated for {} from user {}",
        updatedTarget.toString(),
        subject.getUsername());

    ShareRecordMessageOrRequest shareRecordRequest = (ShareRecordMessageOrRequest) mor;

    BaseRecord record = shareRecordRequest.getRecord();
    User originator = shareRecordRequest.getOriginator();
    User target = shareRecordRequest.getTarget();
    String permission = shareRecordRequest.getPermission();

    ShareConfigElement[] values = new ShareConfigElement[1];
    values[0] = new ShareConfigElement();
    values[0].setUserId(target.getId());
    values[0].setOperation(permission);

    ServiceOperationResult<Set<RecordGroupSharing>> sharingResult =
        sharingManager.shareRecord(originator, record.getId(), values);
    if (sharingResult.isSucceeded()) {
      BaseRecord shared = sharingResult.getEntity().iterator().next().getShared();
      auditService.notify(new ShareRecordAuditEvent(originator, shared, values));
    }
    permissnUtils.notifyUserOrGroupToRefreshCache(target);
  }
}
