package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.WorkbenchApi;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiContainerInfo;
import com.researchspace.api.v1.model.ApiContainerSearchResult;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordType;
import com.researchspace.model.User;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;

public class WorkbenchApiControllerTest extends SpringTransactionalTest {

  private @Autowired WorkbenchApi workbenchApi;

  private BindingResult mockBindingResult = mock(BindingResult.class);

  @Before
  public void setUp() {
    when(mockBindingResult.hasErrors()).thenReturn(false);
  }

  @Test
  public void retrieveDefaultWorkbenchContent() throws BindException {

    User exampleContentUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithExampleContent(exampleContentUser);
    logoutAndLoginAs(exampleContentUser);

    ApiContainerSearchResult visibleWorkbenches =
        workbenchApi.getWorkbenchesForUser(null, null, mockBindingResult, exampleContentUser);
    assertEquals(1, visibleWorkbenches.getTotalHits());
    ApiContainerInfo workbenchInfo = visibleWorkbenches.getContainers().get(0);
    assertEquals(exampleContentUser.getUsername(), workbenchInfo.getOwner().getUsername());
    assertEquals(1, workbenchInfo.getContentSummary().getTotalCount());

    ApiContainer workbench =
        workbenchApi.getWorkbenchById(workbenchInfo.getId(), true, exampleContentUser);
    assertEquals(1, workbench.getContentSummary().getTotalCount());
    ApiInventoryRecordInfo workbenchRecord = workbench.getStoredContent().get(0);
    assertEquals("Complex Sample #1.01", workbenchRecord.getName());
    assertEquals(ApiInventoryRecordType.SUBSAMPLE, workbenchRecord.getType());

    // without content
    ApiContainer workbenchWithoutContent =
        workbenchApi.getWorkbenchById(workbenchInfo.getId(), false, exampleContentUser);
    assertEquals(1, workbench.getContentSummary().getTotalCount());
    assertNull(workbenchWithoutContent.getStoredContent());
  }

  @Test
  public void containerRequestsBlockedByWorkbenchController() throws BindException {
    User user = createInitAndLoginAnyUser();
    ApiContainer container = createBasicContainerForUser(user);
    assertEquals("LIST", container.getCType());

    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> workbenchApi.getWorkbenchById(container.getId(), true, user));
    assertEquals(
        "Container with id " + container.getId() + " is not a workbench", iae.getMessage());
  }
}
