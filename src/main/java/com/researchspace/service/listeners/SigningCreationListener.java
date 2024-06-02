package com.researchspace.service.listeners;

import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.SigningEvent;
import com.researchspace.model.events.CreationEvent;
import com.researchspace.model.views.SigningResult;
import com.researchspace.service.PostSigningManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class SigningCreationListener {

  private @Autowired PostSigningManager postSigningManager;
  private @Autowired AuditTrailService auditService;

  @TransactionalEventListener(condition = "#signatureCreatedEvent.createdItem.successful")
  public void signatureCreated(CreationEvent<SigningResult> signatureCreatedEvent) {
    SigningResult result = signatureCreatedEvent.getCreatedItem();
    auditService.notify(
        new SigningEvent(result.getSigned(), result.getSignature().get().getSigner()));
    postSigningManager.postRecordSign(result);
  }
}
