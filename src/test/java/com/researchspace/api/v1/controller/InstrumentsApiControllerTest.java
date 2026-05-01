package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiContainerLocation;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiLinkItem;
import com.researchspace.api.v1.model.ApiTargetLocation;
import com.researchspace.model.User;
import com.researchspace.testutils.SpringTransactionalTest;
import javax.ws.rs.NotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;

public class InstrumentsApiControllerTest extends SpringTransactionalTest {

  @Autowired private InstrumentsApiController instrumentsApi;

  private final BindingResult mockBindingResult = mock(BindingResult.class);
  private User testUser;

  @Before
  public void setUp() {
    testUser = createInitAndLoginAnyUser();
    assertTrue(testUser.isContentInitialized());
    ReflectionTestUtils.setField(instrumentsApi, "inventoryInstrumentEnabled", true);
    when(mockBindingResult.hasErrors()).thenReturn(false);
  }

  @Test
  public void createAndRetrieveInstrument() throws Exception {
    ApiInstrument request = new ApiInstrument();
    request.setName("controller instrument");

    ApiInstrument created =
        instrumentsApi.createNewInstrument(request, mockBindingResult, testUser);
    assertNotNull(created);
    assertNotNull(created.getId());
    assertEquals("controller instrument", created.getName());
    assertNotNull(created.getOwner());
    assertEquals(testUser.getUsername(), created.getOwner().getUsername());
    assertFalse(created.getLinks().isEmpty());
    assertTrue(created.getLinkOfType(ApiLinkItem.SELF_REL).isPresent());
    assertTrue(
        created
            .getLinkOfType(ApiLinkItem.SELF_REL)
            .orElseThrow(() -> new IllegalStateException("missing self link"))
            .getLink()
            .endsWith("/api/inventory/v1/instruments/" + created.getId()));

    ApiInstrument retrieved = instrumentsApi.getInstrumentById(created.getId(), testUser);
    assertNotNull(retrieved);
    assertEquals(created.getId(), retrieved.getId());
    assertEquals(created.getName(), retrieved.getName());
    assertEquals(testUser.getUsername(), retrieved.getOwner().getUsername());
    assertFalse(retrieved.getLinks().isEmpty());
    assertTrue(retrieved.getLinkOfType(ApiLinkItem.SELF_REL).isPresent());
  }

  @Test
  public void createInstrumentWithNewTargetLocationAssignsContainerParent() throws Exception {
    ApiContainer gridContainer = createBasicGridContainerForUser(testUser, 2, 2);

    ApiInstrument request = new ApiInstrument();
    request.setName("instrument in grid");
    ApiContainerLocation targetLocation = new ApiContainerLocation(1, 2);
    request.setNewTargetLocation(new ApiTargetLocation(gridContainer.getId(), targetLocation));

    ApiInstrument created =
        instrumentsApi.createNewInstrument(request, mockBindingResult, testUser);

    assertNotNull(request.getParentContainer());
    assertEquals(gridContainer.getId(), request.getParentContainer().getId());
    assertEquals(targetLocation, request.getParentLocation());
    assertTrue(created.isStoredInContainer());
    assertNotNull(created.getParentContainers());
    assertEquals(gridContainer.getId(), created.getParentContainers().get(0).getId());
    assertNotNull(created.getParentLocation());
    assertEquals(Integer.valueOf(1), created.getParentLocation().getCoordX());
    assertEquals(Integer.valueOf(2), created.getParentLocation().getCoordY());
  }

  @Test
  public void createInstrumentRequiresName() {
    ApiInstrument request = new ApiInstrument();
    BindingResult bindingResult = new BeanPropertyBindingResult(request, "apiInstrument");

    BindException bindException =
        assertThrows(
            BindException.class,
            () -> instrumentsApi.createNewInstrument(request, bindingResult, testUser));

    assertEquals(1, bindException.getErrorCount());
    assertNotNull(bindException.getFieldError());
    assertEquals("name", bindException.getFieldError().getField());
    assertEquals("errors.required", bindException.getFieldError().getCode());
  }

  @Test
  public void createInstrumentRejectsTooLongName() {
    ApiInstrument request = new ApiInstrument();
    request.setName(StringUtils.leftPad("test", 256, '*'));
    BindingResult bindingResult = new BeanPropertyBindingResult(request, "apiInstrument");

    BindException bindException =
        assertThrows(
            BindException.class,
            () -> instrumentsApi.createNewInstrument(request, bindingResult, testUser));

    assertEquals(1, bindException.getErrorCount());
    assertNotNull(bindException.getFieldError());
    assertEquals("name", bindException.getFieldError().getField());
    assertEquals("errors.maxlength", bindException.getFieldError().getCode());
  }

  @Test
  public void createInstrumentThrowsWhenFeatureDisabled() {
    ReflectionTestUtils.setField(instrumentsApi, "inventoryInstrumentEnabled", false);
    ApiInstrument request = new ApiInstrument();
    request.setName("disabled instrument");

    UnsupportedOperationException unsupportedOperationException =
        assertThrows(
            UnsupportedOperationException.class,
            () -> instrumentsApi.createNewInstrument(request, mockBindingResult, testUser));

    assertEquals(
        "The inventory Instrument is not enabled in this RSpace instance",
        unsupportedOperationException.getMessage());
  }

  @Test
  public void getInstrumentByIdThrowsNotFoundIfMissing() {
    NotFoundException notFoundException =
        assertThrows(
            NotFoundException.class,
            () -> instrumentsApi.getInstrumentById(Long.MAX_VALUE, testUser));

    assertTrue(notFoundException.getMessage().contains("Inventory Instrument"));
    assertTrue(notFoundException.getMessage().contains(Long.toString(Long.MAX_VALUE)));
  }
}
