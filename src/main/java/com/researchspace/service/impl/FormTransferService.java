package com.researchspace.service.impl;

import com.researchspace.dao.FormDao;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.record.RSForm;
import com.researchspace.service.TransferService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FormTransferService implements TransferService {
  private final FormDao formDao;
  private final AuditTrailService auditTrailService;

  @Autowired
  public FormTransferService(FormDao formDao, AuditTrailService auditTrailService) {
    this.formDao = formDao;
    this.auditTrailService = auditTrailService;
  }

  @Transactional
  public void transferOwnership(User originalOwner, User newOwner) {
    List<RSForm> formsUsedByOtherUsers = formDao.getFormsUsedByOtherUsers(originalOwner);
    List<Long> formIds =
        formsUsedByOtherUsers.stream().map(RSForm::getId).collect(Collectors.toList());
    formDao.transferOwnershipOfForms(originalOwner, newOwner, formIds);
    String description =
        String.format(
            "Ownership of form transferred from %s to %s",
            originalOwner.getUsername(), newOwner.getUsername());
    for (RSForm form : formsUsedByOtherUsers) {
      auditTrailService.notify(new GenericEvent(newOwner, form, AuditAction.TRANSFER, description));
    }
  }
}
