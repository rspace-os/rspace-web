package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiPidinstImportPost;
import com.researchspace.api.v1.model.ApiPidinstSearchResult;
import com.researchspace.api.v1.model.ApiTargetLocation;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.inventory.PidinstLookupManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.BindingResult;

@ExtendWith(MockitoExtension.class)
class PidinstLookupApiControllerTest {

  @Mock private PidinstLookupManager pidinstLookupMgr;
  @Mock private ApiAvailabilityHandler apiHandler;
  @Mock private IPropertyHolder properties;
  @InjectMocks private PidinstLookupApiController controller;
  @InjectMocks private InstrumentsApiController instrumentsController;
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

  @Test
  void importNeedsInventoryOnlyAndPassesTheRegistryThrough() throws Exception {
    ApiPidinstImportPost post = new ApiPidinstImportPost();
    post.setPid("10.1/abc");
    post.setProvider("PIDINST_DATACITE");
    ApiTargetLocation location = new ApiTargetLocation();
    post.setNewTargetLocation(location);
    ApiInstrument created = mock(ApiInstrument.class);
    when(properties.getServerUrl()).thenReturn("http://localhost:8080");
    when(pidinstLookupMgr.importInstrument("10.1/abc", "PIDINST_DATACITE", location, user))
        .thenReturn(created);

    assertSame(created, instrumentsController.importPidinst(post, mock(BindingResult.class), user));

    InOrder inOrder = inOrder(apiHandler, pidinstLookupMgr);
    inOrder.verify(apiHandler).assertInventoryAvailable(user);
    inOrder
        .verify(pidinstLookupMgr)
        .importInstrument("10.1/abc", "PIDINST_DATACITE", location, user);
    verify(apiHandler, never()).assertInventoryAndIdentifierTypeEnabled(any(), any());
  }
}
