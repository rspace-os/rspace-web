package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.model.User;
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
}
