package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiPidinstSearchResult;
import com.researchspace.model.User;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.inventory.PidinstLookupManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PidinstLookupApiControllerTest {

  @Mock private PidinstLookupManager pidinstLookupMgr;
  @Mock private ApiAvailabilityHandler apiHandler;
  @InjectMocks private PidinstLookupApiController controller;
  private final User user = new User("jane");

  @Test
  void searchNeedsInventoryOnlyAndPassesTheRegistriesAndPageThrough() {
    ApiPidinstSearchResult page = new ApiPidinstSearchResult();
    when(pidinstLookupMgr.search("Zeiss", List.of("PIDINST_B2INST"), 2, user)).thenReturn(page);

    assertSame(page, controller.search("Zeiss", List.of("PIDINST_B2INST"), 2, user));

    InOrder inOrder = inOrder(apiHandler, pidinstLookupMgr);
    inOrder.verify(apiHandler).assertInventoryAvailable(user);
    inOrder.verify(pidinstLookupMgr).search("Zeiss", List.of("PIDINST_B2INST"), 2, user);
    verify(apiHandler, never()).assertInventoryAndIdentifierTypeEnabled(any(), any());
  }
}
