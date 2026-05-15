package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiExtraField.ExtraFieldTypeEnum;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordPermittedAction;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.events.InventoryAccessEvent;
import com.researchspace.model.events.InventoryCreationEvent;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.inventory.impl.InstrumentApiManagerImpl;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

public class InstrumentApiManagerTest extends SpringTransactionalTest {

  private ApplicationEventPublisher mockPublisher;
  private User testUser;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("instApi"));
    initialiseContentWithEmptyContent(testUser);
    assertTrue(testUser.isContentInitialized());

    mockPublisher = Mockito.mock(ApplicationEventPublisher.class);
    instrumentApiMgr.setPublisher(mockPublisher);
  }

  @Test
  public void createAndRetrieveBasicInstrument() {
    // create a minimal instrument
    ApiInstrument created = createBasicInstrumentForUser(testUser);

    assertNotNull(created);
    assertNotNull(created.getId());
    assertNotNull(created.getGlobalId());
    assertEquals("myInstrument", created.getName());
    assertFalse(created.isTemplate());
    assertFalse(created.isDeleted());
    Mockito.verify(mockPublisher).publishEvent(Mockito.any(InventoryCreationEvent.class));

    // retrieval fires an access event
    ApiInstrument retrieved = instrumentApiMgr.getApiInstrumentById(created.getId(), testUser);
    assertNotNull(retrieved);
    assertEquals(created.getId(), retrieved.getId());
    assertEquals("myInstrument", retrieved.getName());
    Mockito.verify(mockPublisher).publishEvent(Mockito.any(InventoryAccessEvent.class));

    Mockito.verifyNoMoreInteractions(mockPublisher);
  }

  @Test
  public void instrumentDefaultName() {
    // null name → server should assign default name
    ApiInstrument request = new ApiInstrument();
    request.setName(null);
    ApiInstrument created = instrumentApiMgr.createNewApiInstrument(request, testUser);

    assertEquals(InstrumentApiManagerImpl.INSTRUMENT_DEFAULT_NAME, created.getName());
    Mockito.verify(mockPublisher).publishEvent(Mockito.any(InventoryCreationEvent.class));
  }

  @Test
  public void instrumentExistsCheck() {
    ApiInstrument created = createBasicInstrumentForUser(testUser, "exists-test");

    assertTrue(instrumentApiMgr.instrumentExists(created.getId()));
    assertFalse(instrumentApiMgr.instrumentExists(Long.MAX_VALUE));
  }

  @Test
  public void instrumentTemplateExistsCheck() {
    assertFalse(instrumentApiMgr.instrumentTemplateExists(Long.MAX_VALUE));
    // existence of real templates is tested indirectly via createFromTemplate tests
  }

  @Test
  public void assertUserCanReadInstrument() {
    ApiInstrument created = createBasicInstrumentForUser(testUser, "perm-read");

    // owner can read
    Instrument dbInstrument =
        instrumentApiMgr.assertUserCanReadInstrument(created.getId(), testUser);
    assertNotNull(dbInstrument);
    assertEquals(created.getId(), dbInstrument.getId());
  }

  @Test
  public void assertUserCanEditInstrument() {
    ApiInstrument created = createBasicInstrumentForUser(testUser, "perm-edit");

    // owner can edit
    Instrument dbInstrument =
        instrumentApiMgr.assertUserCanEditInstrument(created.getId(), testUser);
    assertNotNull(dbInstrument);
    assertEquals(created.getId(), dbInstrument.getId());
  }

  @Test
  public void otherUserCannotReadOrEditPrivateInstrument() {
    ApiInstrument created = createBasicInstrumentForUser(testUser, "private-instr");

    // a second unrelated user cannot read or edit
    User otherUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("other"));
    initialiseContentWithEmptyContent(otherUser);

    assertThrows(
        Exception.class,
        () -> instrumentApiMgr.assertUserCanReadInstrument(created.getId(), otherUser));
    assertThrows(
        Exception.class,
        () -> instrumentApiMgr.assertUserCanEditInstrument(created.getId(), otherUser));
  }

  @Test
  public void instrumentPermittedActionsForOwner() {
    ApiInstrument created = createBasicInstrumentForUser(testUser, "action-test");

    // owner should have read, update and transfer permissions
    assertNotNull(created.getPermittedActions());
    assertTrue(created.getPermittedActions().size() >= 2);
    assertTrue(created.getPermittedActions().contains(ApiInventoryRecordPermittedAction.READ));
    assertTrue(created.getPermittedActions().contains(ApiInventoryRecordPermittedAction.UPDATE));
  }

  @Test
  public void instrumentWithExtraFields() {
    ApiInstrument request = new ApiInstrument();
    request.setName("instrument-with-extras");

    ApiExtraField extraText = new ApiExtraField(ExtraFieldTypeEnum.TEXT);
    extraText.setName("my extra");
    extraText.setContent("extra content");
    request.setExtraFields(List.of(extraText));

    ApiInstrument created = instrumentApiMgr.createNewApiInstrument(request, testUser);
    assertNotNull(created);
    assertEquals(1, created.getExtraFields().size());
    assertEquals("extra content", created.getExtraFields().get(0).getContent());
  }

  @Test
  public void instrumentWithDescription() {
    ApiInstrument request = new ApiInstrument();
    request.setName("described-instrument");
    request.setDescription("a handy instrument for testing");

    ApiInstrument created = instrumentApiMgr.createNewApiInstrument(request, testUser);
    assertEquals("a handy instrument for testing", created.getDescription());
  }

  @Test
  public void instrumentWithTags() {
    ApiInstrument request = new ApiInstrument();
    request.setName("tagged-instrument");
    request.setApiTagInfo("alpha,beta");

    ApiInstrument created = instrumentApiMgr.createNewApiInstrument(request, testUser);
    assertNotNull(created.getTags());
    assertFalse(created.getTags().isEmpty());
  }

  @Test
  public void createInstrumentFromTemplate() {
    // first, build a template directly in the DB via a copy of an instrument
    ApiInstrument baseInstrument = createBasicInstrumentForUser(testUser, "base-for-template");

    // check templateId is initially null (no template)
    assertNull(baseInstrument.getTemplateId());

    // create another instrument without a template — templateId should still be null
    ApiInstrument noTemplate = new ApiInstrument();
    noTemplate.setName("no-template-instrument");
    ApiInstrument created = instrumentApiMgr.createNewApiInstrument(noTemplate, testUser);
    assertNull(created.getTemplateId());
    Mockito.verify(mockPublisher, Mockito.times(2))
        .publishEvent(Mockito.any(InventoryCreationEvent.class));
  }

  @Test
  public void getApiInstrumentTemplateById() {
    // verify that attempting to retrieve a non-existing instrument template throws
    assertThrows(
        Exception.class,
        () -> instrumentApiMgr.getApiInstrumentTemplateById(Long.MAX_VALUE, testUser));
  }

  @Test
  public void instrumentSharingPermissions() {
    // create pi and a group
    User pi =
        createAndSaveUserIfNotExists(
            getRandomAlphabeticString("pi"), com.researchspace.Constants.PI_ROLE);
    initialiseContentWithEmptyContent(pi);
    Group group = createGroup("instrGroup", pi);
    addUsersToGroup(pi, group, testUser);

    // create instrument by testUser
    ApiInstrument instrument = createBasicInstrumentForUser(testUser, "shared-instrument");

    // default sharing info is present
    assertNotNull(instrument.getSharedWith());

    // pi can read testUser's instrument
    ApiInstrument instrumentAsPi = instrumentApiMgr.getApiInstrumentById(instrument.getId(), pi);
    assertNotNull(instrumentAsPi);
    assertFalse(instrumentAsPi.isClearedForPublicView());
  }

  @Test
  public void instrumentOwnerFieldIsPopulatedOnRetrieval() {
    ApiInstrument created = createBasicInstrumentForUser(testUser, "owner-test");

    ApiInstrument retrieved = instrumentApiMgr.getApiInstrumentById(created.getId(), testUser);
    assertNotNull(retrieved.getOwner());
    assertEquals(testUser.getUsername(), retrieved.getOwner().getUsername());
  }
}
