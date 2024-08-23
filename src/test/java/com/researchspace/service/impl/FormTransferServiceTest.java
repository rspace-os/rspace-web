package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.dao.FormDao;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.record.RSForm;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FormTransferServiceTest {

  @Mock FormDao formDao;

  @Mock AuditTrailService auditTrailService;

  @InjectMocks FormTransferService formTransferService;

  User originalOwner;

  User newOwner;

  @Test
  public void testUsesCorrectParamsAndNotifiesAuditTrail() {
    formTransferService = new FormTransferService(formDao, auditTrailService);

    originalOwner = new User();
    originalOwner.setUsername("original");

    newOwner = new User();
    newOwner.setUsername("new");

    // 2 forms owned by originalOwner
    RSForm form1 = new RSForm();
    form1.setId(1L);
    form1.setOwner(originalOwner);
    RSForm form2 = new RSForm();
    form2.setId(2L);
    form2.setOwner(originalOwner);

    List<RSForm> originalOwnersForms = List.of(form1, form2);
    Mockito.when(formDao.getFormsUsedByOtherUsers(Mockito.any(User.class)))
        .thenReturn(originalOwnersForms);

    formTransferService.transferOwnership(originalOwner, newOwner);

    Mockito.verify(formDao).transferOwnershipOfForms(originalOwner, newOwner, List.of(form1.getId(), form2.getId()));

    ArgumentCaptor<GenericEvent> auditEventCaptor = ArgumentCaptor.forClass(GenericEvent.class);
    Mockito.verify(auditTrailService, Mockito.times(2)).notify(auditEventCaptor.capture());
    assertAuditEvent(auditEventCaptor.getAllValues().get(0), form1);
    assertAuditEvent(auditEventCaptor.getAllValues().get(1), form2);

    Mockito.verifyNoMoreInteractions(auditTrailService);
  }

  private void assertAuditEvent(GenericEvent auditEvent, RSForm expectedForm) {
    String expectedAuditDescription = "Ownership of form transferred from original to new";
    assertEquals(newOwner, auditEvent.getSubject());
    assertEquals(expectedForm, auditEvent.getAuditedObject());
    assertEquals(AuditAction.TRANSFER, auditEvent.getAuditAction());
    assertEquals(expectedAuditDescription, auditEvent.getDescription());
  }
}
