package com.researchspace.api.v1.controller;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSampleWithoutSubSamples;
import com.researchspace.datacite.model.DataCiteDoiAttributes.Affiliation;
import com.researchspace.model.User;
import com.researchspace.service.RoRService;
import com.researchspace.service.inventory.InventoryIdentifierApiManager;
import com.researchspace.webapp.integrations.datacite.DataCiteConnectorDummy;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;

public class InventoryPublicApiControllerMVCIT extends API_MVC_InventoryTestBase {

  @Autowired private InventoryIdentifierApiManager identifierApiManager;
  @Mock private RoRService rorServiceMock;
  private DataCiteConnectorDummy dataCiteConnectorManualMock = new DataCiteConnectorDummy();

  @Before
  public void setup() throws Exception {
    openMocks(this);
    identifierApiManager.setDataCiteConnector(dataCiteConnectorManualMock);
    ReflectionTestUtils.setField(identifierApiManager, "rorService", rorServiceMock);
    when(rorServiceMock.getSystemRoRValue()).thenReturn("rorValue");
    when(rorServiceMock.getRorNameForSystemRoRValue()).thenReturn("rorName");
    super.setUp();
  }

  @Test
  public void getPublishedSampleDetails() throws Exception {

    // create user and a sample
    User anyUser = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples basicSample = createBasicSampleForUser(anyUser);

    // register new draft identifier
    ApiInventoryRecordInfo sampleWithDraftIdentifier =
        identifierApiManager.registerNewIdentifier(basicSample.getOid(), anyUser);
    ApiInventoryDOI draftDoi = sampleWithDraftIdentifier.getIdentifiers().get(0);
    assertEquals(null, draftDoi.getCreatorAffiliation());
    assertEquals(null, dataCiteConnectorManualMock.doiSentToDatacite.getAttributes().getCreators());
    assertEquals(null, draftDoi.getCreatorAffiliationIdentifier());

    // friendly error when requesting non-published item
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForAnonymousGet(
                    API_VERSION.ONE, "/public/view/" + draftDoi.getRsPublicId()))
            .andExpect(status().is4xxClientError())
            .andReturn();
    assertEquals(
        "The item you try to see is not publicly available right now.",
        result.getResolvedException().getMessage());

    // mark as published/findable
    identifierApiManager.publishIdentifier(basicSample.getOid(), anyUser);
    // full details when requesting published item
    result =
        this.mockMvc
            .perform(
                createBuilderForAnonymousGet(
                    API_VERSION.ONE, "/public/view/" + draftDoi.getRsPublicId()))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertNull(result.getResolvedException());
    ApiInventoryRecordInfo foundRecord =
        getFromJsonResponseBody(result, ApiInventoryRecordInfo.class);
    assertNotNull(foundRecord);
    makeAssertionsOnPublished(foundRecord);

    // assert that updated RoR value is present in retracted DOI but published DOI keeps the RoR set
    // at time of publication
    when(rorServiceMock.getSystemRoRValue()).thenReturn("");
    when(rorServiceMock.getRorNameForSystemRoRValue()).thenReturn("");
    result =
        this.mockMvc
            .perform(
                createBuilderForAnonymousGet(
                    API_VERSION.ONE, "/public/view/" + draftDoi.getRsPublicId()))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    foundRecord = getFromJsonResponseBody(result, ApiInventoryRecordInfo.class);
    makeAssertionsOnPublished(foundRecord);

    // mark as retracted/registered
    ApiInventoryRecordInfo retracted =
        identifierApiManager.retractIdentifier(basicSample.getOid(), anyUser);
    assertEquals("", retracted.getIdentifiers().get(0).getCreatorAffiliation());
    assertEquals("", retracted.getIdentifiers().get(0).getCreatorAffiliationIdentifier());
    assertEquals(
        null,
        dataCiteConnectorManualMock
            .getDoiSentToDatacite()
            .getAttributes()
            .getCreators()
            .get(0)
            .getAffiliation());
    // friendly error when requesting retracted item
    result =
        this.mockMvc
            .perform(
                createBuilderForAnonymousGet(
                    API_VERSION.ONE, "/public/view/" + draftDoi.getRsPublicId()))
            .andExpect(status().is4xxClientError())
            .andReturn();
    assertEquals(
        "The item you try to see is not publicly available right now.",
        result.getResolvedException().getMessage());

    // same error when requesting unknown item
    result =
        this.mockMvc
            .perform(createBuilderForAnonymousGet(API_VERSION.ONE, "/public/view/randomDoi"))
            .andExpect(status().is4xxClientError())
            .andReturn();
    assertEquals(
        "The item you try to see is not publicly available right now.",
        result.getResolvedException().getMessage());
  }

  private void makeAssertionsOnPublished(ApiInventoryRecordInfo foundRecord) {
    assertNull(foundRecord.getName());
    assertEquals("mySample", foundRecord.getIdentifiers().get(0).getTitle());
    assertEquals("rorName", foundRecord.getIdentifiers().get(0).getCreatorAffiliation());
    assertEquals("rorValue", foundRecord.getIdentifiers().get(0).getCreatorAffiliationIdentifier());

    Affiliation rorAffilication =
        dataCiteConnectorManualMock
            .getDoiSentToDatacite()
            .getAttributes()
            .getCreators()
            .get(0)
            .getAffiliation()[0];
    assertEquals("rorValue", rorAffilication.getAffiliationIdentifier());
    assertEquals("rorName", rorAffilication.getName());
  }

  @Test
  public void checkOnlyPublicInformationPresentInApiResponse() throws Exception {

    // create user and a sample
    User anyUser = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample = createComplexSampleForUser(anyUser);

    // register new draft identifier, mark as published
    ApiInventoryRecordInfo sampleWithDraftIdentifier =
        identifierApiManager.registerNewIdentifier(sample.getOid(), anyUser);
    ApiInventoryDOI createdDoi = sampleWithDraftIdentifier.getIdentifiers().get(0);
    assertEquals(false, createdDoi.getCustomFieldsOnPublicPage());
    identifierApiManager.publishIdentifier(sample.getOid(), anyUser);

    // check public view API response
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForAnonymousGet(
                    API_VERSION.ONE, "/public/view/" + createdDoi.getRsPublicId()))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertNull(result.getResolvedException());
    ApiInventoryRecordInfo foundRecord =
        getFromJsonResponseBody(result, ApiInventoryRecordInfo.class);
    assertNotNull(foundRecord);
    // expect only identifier property being populated
    assertEquals(1, foundRecord.getIdentifiers().size());
    assertFalse(foundRecord.getIdentifiers().get(0).getCustomFieldsOnPublicPage());
    assertNull(foundRecord.getName());
    assertEquals(0, foundRecord.getTags().size());
    assertNull(foundRecord.getDescription());
    assertEquals(0, ((ApiSampleWithoutSubSamples) foundRecord).getFields().size());
    assertEquals(0, foundRecord.getExtraFields().size());

    // update identifier so custom fields are being published
    ApiSample sampleUpdate = new ApiSample();
    sampleUpdate.setId(sample.getId());
    sampleUpdate.setTags(null);
    ApiInventoryDOI doiUpdate = new ApiInventoryDOI();
    doiUpdate.setId(createdDoi.getId());
    doiUpdate.setCustomFieldsOnPublicPage(true);
    sampleUpdate.getIdentifiers().add(doiUpdate);
    ApiSample updatedSample = sampleApiMgr.updateApiSample(sampleUpdate, anyUser);
    ApiInventoryDOI updatedDoi = updatedSample.getIdentifiers().get(0);
    assertEquals(true, updatedDoi.getCustomFieldsOnPublicPage());

    // re-publish the identifier (to propagate customFieldsOnPublicPage flag)
    identifierApiManager.retractIdentifier(sample.getOid(), anyUser);
    identifierApiManager.publishIdentifier(sample.getOid(), anyUser);

    // reload public view API response
    result =
        this.mockMvc
            .perform(
                createBuilderForAnonymousGet(
                    API_VERSION.ONE, "/public/view/" + createdDoi.getRsPublicId()))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertNull(result.getResolvedException());
    foundRecord = getFromJsonResponseBody(result, ApiInventoryRecordInfo.class);
    assertNotNull(foundRecord);
    // more details expected if customFieldsOnPublicPage=true (RSDEV-76)
    assertEquals(1, foundRecord.getIdentifiers().size());
    assertTrue(foundRecord.getIdentifiers().get(0).getCustomFieldsOnPublicPage());
    assertNull(foundRecord.getName()); // name always null
    assertEquals(1, foundRecord.getTags().size());
    assertEquals("complexSampleDescription", foundRecord.getDescription());
    assertEquals(9, ((ApiSampleWithoutSubSamples) foundRecord).getFields().size());
    assertEquals(1, foundRecord.getExtraFields().size());
  }
}
