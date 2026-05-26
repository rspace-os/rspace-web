package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentSearchResult;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.service.inventory.impl.InstrumentApiManagerImpl;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import org.junit.Test;

public class InstrumentApiManagerIT extends RealTransactionSpringTestBase {

  @Test
  public void checkBasicInstrumentCreateRetrieveAndExists() throws Exception {
    User testUser = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(testUser);

    ApiInstrument created = createBasicInstrumentForUser(testUser, "it-instrument");
    assertNotNull(created.getId());
    assertEquals("it-instrument", created.getName());
    assertFalse(created.isTemplate());

    assertTrue(instrumentApiMgr.instrumentExists(created.getId()));

    ApiInstrument retrieved = instrumentApiMgr.getApiInstrumentById(created.getId(), testUser);
    assertEquals(created.getId(), retrieved.getId());
    assertEquals("it-instrument", retrieved.getName());
  }

  @Test
  public void checkDefaultInstrumentNameApplied() throws Exception {
    User testUser = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(testUser);

    ApiInstrument request = new ApiInstrument();
    request.setName(" ");

    ApiInstrument created = instrumentApiMgr.createNewApiInstrument(request, testUser);
    assertEquals(InstrumentApiManagerImpl.INSTRUMENT_DEFAULT_NAME, created.getName());
  }

  @Test
  public void checkInstrumentPermissionAssertions() throws Exception {
    User owner = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(owner);
    ApiInstrument created = createBasicInstrumentForUser(owner, "permissions-it");

    assertNotNull(instrumentApiMgr.assertUserCanReadInstrument(created.getId(), owner));
    assertNotNull(instrumentApiMgr.assertUserCanEditInstrument(created.getId(), owner));

    User otherUser = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(otherUser);
    assertThrows(
        Exception.class,
        () -> instrumentApiMgr.assertUserCanEditInstrument(created.getId(), otherUser));
  }

  @Test
  public void checkInventoryEntityFieldPermissionRoutingForSample() throws Exception {
    User testUser = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithInitialisedContent(testUser);

    ApiSampleWithFullSubSamples complexSample = createComplexSampleForUser(testUser);
    Long sampleFieldId = complexSample.getFields().get(0).getId();

    InventoryRecord readResult =
        instrumentApiMgr.assertUserCanReadInventoryEntityField(sampleFieldId, testUser);
    assertEquals(complexSample.getId(), readResult.getId());

    InventoryRecord editResult =
        instrumentApiMgr.assertUserCanEditInventoryEntityField(sampleFieldId, testUser);
    assertEquals(complexSample.getId(), editResult.getId());
  }

  @Test
  public void checkTemplateMethodsForMissingTemplate() throws Exception {
    User testUser = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(testUser);

    assertFalse(instrumentApiMgr.instrumentTemplateExists(Long.MAX_VALUE));
    assertThrows(
        Exception.class,
        () -> instrumentApiMgr.getApiInstrumentTemplateById(Long.MAX_VALUE, testUser));
  }

  @Test
  public void updateApiInstrument_persistsChanges() throws Exception {
    User testUser = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(testUser);

    ApiInstrument created = createBasicInstrumentForUser(testUser, "before-update");

    created.setName("after-update");
    created.setDescription("a new description");
    instrumentApiMgr.updateApiInstrument(created, testUser);

    ApiInstrument retrieved = instrumentApiMgr.getApiInstrumentById(created.getId(), testUser);
    assertEquals("after-update", retrieved.getName());
    assertEquals("a new description", retrieved.getDescription());
  }

  @Test
  public void markInstrumentAsDeleted_andRestore() throws Exception {
    User testUser = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(testUser);

    ApiInstrument created = createBasicInstrumentForUser(testUser, "soft-delete-it");
    assertFalse(created.isDeleted());

    ApiInstrument deleted = instrumentApiMgr.markInstrumentAsDeleted(created.getId(), testUser);
    assertTrue(deleted.isDeleted());

    // deleted instrument still appears when including deleted records
    ApiInstrumentSearchResult withDeleted =
        instrumentApiMgr.getInstrumentsForUser(
            PaginationCriteria.createDefaultForClass(Instrument.class),
            null,
            InventorySearchDeletedOption.DELETED_ONLY,
            testUser);
    assertTrue(
        withDeleted.getInstruments().stream().anyMatch(i -> i.getId().equals(created.getId())));

    ApiInstrument restored = instrumentApiMgr.restoreDeletedInstrument(created.getId(), testUser);
    assertFalse(restored.isDeleted());
  }

  @Test
  public void duplicateInstrument_createsPersistentCopy() throws Exception {
    User testUser = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(testUser);

    ApiInstrument original = createBasicInstrumentForUser(testUser, "to-be-duplicated");

    ApiInstrument copy = instrumentApiMgr.duplicateInstrument(original.getId(), testUser);

    assertNotNull(copy.getId());
    assertFalse(copy.getId().equals(original.getId()));
    assertEquals(original.getName() + "_COPY", copy.getName());
    assertTrue(instrumentApiMgr.instrumentExists(copy.getId()));
  }

  @Test
  public void getInstrumentsForUser_returnsOwnedInstruments() throws Exception {
    User testUser = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(testUser);
    createBasicInstrumentForUser(testUser, "listed-it-instrument");

    ApiInstrumentSearchResult result =
        instrumentApiMgr.getInstrumentsForUser(
            PaginationCriteria.createDefaultForClass(Instrument.class),
            null,
            InventorySearchDeletedOption.EXCLUDE,
            testUser);

    assertTrue(result.getTotalHits() >= 1);
    assertTrue(
        result.getInstruments().stream().anyMatch(i -> i.getName().equals("listed-it-instrument")));
  }

  @Test
  public void nameExistsForUser_detectsExistingName() throws Exception {
    User testUser = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(testUser);
    String name = "unique-it-" + CoreTestUtils.getRandomName(6);

    assertFalse(instrumentApiMgr.nameExistsForUser(name, testUser));

    createBasicInstrumentForUser(testUser, name);

    assertTrue(instrumentApiMgr.nameExistsForUser(name, testUser));
  }

  @Test
  public void changeApiInstrumentOwner_transfersOwnership() throws Exception {
    User owner = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(owner);
    User newOwner = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(newOwner);

    ApiInstrument created = createBasicInstrumentForUser(owner, "to-be-transferred");
    assertEquals(owner.getUsername(), created.getOwner().getUsername());

    created.setOwner(new ApiUser(newOwner));
    ApiInstrument transferred = instrumentApiMgr.changeApiInstrumentOwner(created, owner);

    assertEquals(newOwner.getUsername(), transferred.getOwner().getUsername());

    // new owner can read it; original owner no longer has access
    assertNotNull(instrumentApiMgr.assertUserCanReadInstrument(transferred.getId(), newOwner));
    assertThrows(
        Exception.class,
        () -> instrumentApiMgr.assertUserCanEditInstrument(transferred.getId(), owner));
  }

  @Test
  public void assertUserCanDeleteInstrument_ownerCanDeleteOtherUserCannot() throws Exception {
    User owner = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(owner);
    User other = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(other);

    ApiInstrument created = createBasicInstrumentForUser(owner, "perm-delete-it");

    assertNotNull(instrumentApiMgr.assertUserCanDeleteInstrument(created.getId(), owner));
    assertThrows(
        Exception.class,
        () -> instrumentApiMgr.assertUserCanDeleteInstrument(created.getId(), other));
  }

  @Test
  public void assertUserCanTransferInstrument_ownerCanTransferOtherUserCannot() throws Exception {
    User owner = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(owner);
    User other = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(other);

    ApiInstrument created = createBasicInstrumentForUser(owner, "perm-transfer-it");

    assertNotNull(instrumentApiMgr.assertUserCanTransferInstrument(created.getId(), owner));
    assertThrows(
        Exception.class,
        () -> instrumentApiMgr.assertUserCanTransferInstrument(created.getId(), other));
  }
}
