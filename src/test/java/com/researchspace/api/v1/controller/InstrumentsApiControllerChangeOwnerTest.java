package com.researchspace.api.v1.controller;

import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.UserManager;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.service.inventory.InventoryIdentifierExternalUpdateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;

@ExtendWith(MockitoExtension.class)
class InstrumentsApiControllerChangeOwnerTest {

  @Mock private InstrumentEntityApiManager instrumentApiMgr;
  @Mock private InventoryIdentifierExternalUpdateService externalUpdateService;
  @Mock private UserManager userManager;
  @Mock private IPropertyHolder properties;
  @InjectMocks private InstrumentsApiController controller;

  @Test
  void transferWhileOperatingAsRecordsTheOriginalSysadminAsActor() throws BindException {
    User subject = new User("operated.as");
    User sysadmin = new User("sysadmin");
    ApiInstrument incoming = new ApiInstrument();
    ApiInstrument updated = new ApiInstrument();
    updated.setId(7L);
    when(userManager.getOriginalUserForOperateAs(subject)).thenReturn(sysadmin);
    when(instrumentApiMgr.changeApiInstrumentOwner(same(incoming), same(subject), same(sysadmin)))
        .thenReturn(updated);
    when(properties.getServerUrl()).thenReturn("http://localhost:8080");

    controller.changeInstrumentOwner(
        7L, incoming, new BeanPropertyBindingResult(incoming, "instrument"), subject);

    verify(instrumentApiMgr).assertUserCanTransferInstrument(7L, subject);
    verify(instrumentApiMgr).changeApiInstrumentOwner(incoming, subject, sysadmin);
  }
}
