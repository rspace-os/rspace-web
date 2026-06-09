package com.researchspace.api.v1.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordPermittedAction;
import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList;
import com.researchspace.api.v1.model.ApiLinkItem;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SampleSource;
import com.researchspace.model.units.RSUnitDef;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end coverage of the user-version endpoints added for RSDEV-1141: GET
 * /{samples|subSamples|containers}/{id}/versions/{version} and the container revisions endpoints,
 * exercising real Envers auditing.
 */
// instrument endpoints are gated behind this feature flag, off by default
@TestPropertySource(properties = {"inventory.instrument.enabled=true"})
public class InventoryVersionsApiMVCIT extends API_MVC_InventoryTestBase {

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void sampleVersionRetrieval() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    ApiSampleWithFullSubSamples createdSample =
        createBasicSampleForUser(anyUser, "version one name");

    // edit the sample, bumping it to version 2
    this.mockMvc
        .perform(
            createBuilderForPutWithJSONBody(
                apiKey,
                "/samples/" + createdSample.getId(),
                anyUser,
                "{ \"name\": \"version two name\" }"))
        .andExpect(status().isOk())
        .andReturn();

    // version 1 resolves to the historical snapshot
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/samples/" + createdSample.getId() + "/versions/1",
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiSample sampleV1 = getFromJsonResponseBody(result, ApiSample.class);
    assertEquals("version one name", sampleV1.getName());
    assertTrue(sampleV1.isHistoricalVersion());
    assertEquals(Long.valueOf(1L), sampleV1.getVersion());
    assertNotNull(sampleV1.getRevisionId());
    assertEquals("SA" + createdSample.getId() + "v1", sampleV1.getGlobalId());
    // permitted actions are evaluated against the live record: the owner gets a full view,
    // not the cleared public one
    assertTrue(sampleV1.getPermittedActions().contains(ApiInventoryRecordPermittedAction.READ));

    // the current version resolves to the live record, not flagged historical
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/samples/" + createdSample.getId() + "/versions/2",
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiSample sampleV2 = getFromJsonResponseBody(result, ApiSample.class);
    assertEquals("version two name", sampleV2.getName());
    assertEquals(false, sampleV2.isHistoricalVersion());

    // a version that never existed is a clean 404
    this.mockMvc
        .perform(
            createBuilderForGet(
                API_VERSION.ONE,
                apiKey,
                "/samples/" + createdSample.getId() + "/versions/99",
                anyUser))
        .andExpect(status().isNotFound())
        .andReturn();
  }

  @Test
  public void subSampleVersionRetrieval() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    ApiSampleWithFullSubSamples createdSample = createBasicSampleForUser(anyUser);
    Long subSampleId = createdSample.getSubSamples().get(0).getId();

    // edit the subsample, bumping it to version 2
    this.mockMvc
        .perform(
            createBuilderForPutWithJSONBody(
                apiKey,
                "/subSamples/" + subSampleId,
                anyUser,
                "{ \"name\": \"subsample renamed\" }"))
        .andExpect(status().isOk())
        .andReturn();

    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE, apiKey, "/subSamples/" + subSampleId + "/versions/1", anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiSubSample subSampleV1 = getFromJsonResponseBody(result, ApiSubSample.class);
    assertEquals("mySubSample", subSampleV1.getName());
    assertTrue(subSampleV1.isHistoricalVersion());
    assertEquals(Long.valueOf(1L), subSampleV1.getVersion());
    assertEquals("SS" + subSampleId + "v1", subSampleV1.getGlobalId());
    assertTrue(subSampleV1.getPermittedActions().contains(ApiInventoryRecordPermittedAction.READ));

    // the current version resolves to the live record; the subsample sits in the user's
    // workbench, so this exercises lazy parent-container relations across the
    // controller-to-manager transaction boundary (RSDEV-1141)
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE, apiKey, "/subSamples/" + subSampleId + "/versions/2", anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiSubSample subSampleV2 = getFromJsonResponseBody(result, ApiSubSample.class);
    assertEquals("subsample renamed", subSampleV2.getName());
    assertEquals(false, subSampleV2.isHistoricalVersion());

    this.mockMvc
        .perform(
            createBuilderForGet(
                API_VERSION.ONE, apiKey, "/subSamples/" + subSampleId + "/versions/99", anyUser))
        .andExpect(status().isNotFound())
        .andReturn();
  }

  @Test
  public void containerRevisionsAndVersionRetrieval() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    ApiContainer container = createBasicContainerForUser(anyUser, "version one container");

    // edit the container, bumping it to version 2; the locations image makes the audit
    // snapshot carry a lazy FileProperty that link building must survive (RSDEV-1141)
    this.mockMvc
        .perform(
            createBuilderForPutWithJSONBody(
                apiKey,
                "/containers/" + container.getId(),
                anyUser,
                "{ \"name\": \"version two container\","
                    + " \"newBase64LocationsImage\":\"data:image/jpeg;base64,dummy123\" }"))
        .andExpect(status().isOk())
        .andReturn();

    // revisions list is now available for containers
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/containers/" + container.getId() + "/revisions",
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiInventoryRecordRevisionList history =
        getFromJsonResponseBody(result, ApiInventoryRecordRevisionList.class);
    assertEquals(2, history.getRevisions().size());

    // the version-2 snapshot has a locations image, exposed as a link
    assertTrue(
        history.getRevisions().get(1).getRecord().getLinks().stream()
            .anyMatch(link -> ApiLinkItem.LOCATIONS_IMAGE_REL.equals(link.getRel())));

    // single revision retrieval, without content
    Long firstRevisionId = history.getRevisions().get(0).getRevisionId();
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/containers/" + container.getId() + "/revisions/" + firstRevisionId,
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiContainer containerRev = getFromJsonResponseBody(result, ApiContainer.class);
    assertEquals("version one container", containerRev.getName());
    // locations are not audited, so historical containers are returned without content
    assertNull(containerRev.getLocations());

    // the image-bearing revision also resolves individually, with its locations image link
    Long secondRevisionId = history.getRevisions().get(1).getRevisionId();
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/containers/" + container.getId() + "/revisions/" + secondRevisionId,
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiContainer imageBearingRev = getFromJsonResponseBody(result, ApiContainer.class);
    assertEquals("version two container", imageBearingRev.getName());
    assertTrue(
        imageBearingRev.getLinks().stream()
            .anyMatch(link -> ApiLinkItem.LOCATIONS_IMAGE_REL.equals(link.getRel())));

    // version lookup resolves the historical snapshot
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/containers/" + container.getId() + "/versions/1",
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiContainer containerV1 = getFromJsonResponseBody(result, ApiContainer.class);
    assertEquals("version one container", containerV1.getName());
    assertTrue(containerV1.isHistoricalVersion());
    assertEquals("IC" + container.getId() + "v1", containerV1.getGlobalId());
    assertNull(containerV1.getLocations());
    assertTrue(containerV1.getPermittedActions().contains(ApiInventoryRecordPermittedAction.READ));

    // the current version resolves to the live record, content included
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/containers/" + container.getId() + "/versions/2",
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiContainer containerV2 = getFromJsonResponseBody(result, ApiContainer.class);
    assertEquals("version two container", containerV2.getName());
    assertEquals(false, containerV2.isHistoricalVersion());
    assertNotNull(containerV2.getLocations());

    // a revision before this container existed is a clean 404, not a 200 null body.
    // (Envers resolves a too-high revision number as-of the latest snapshot, so a
    // revision predating creation is the deterministic missing-revision case.)
    this.mockMvc
        .perform(
            createBuilderForGet(
                API_VERSION.ONE,
                apiKey,
                "/containers/" + container.getId() + "/revisions/" + (firstRevisionId - 1),
                anyUser))
        .andExpect(status().isNotFound())
        .andReturn();
  }

  @Test
  public void instrumentVersionRetrieval() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey, "/instruments", anyUser, "{ \"name\": \"version one instrument\" }"))
            .andExpect(status().isCreated())
            .andReturn();
    ApiInstrument instrument = getFromJsonResponseBody(result, ApiInstrument.class);

    // edit the instrument, bumping it to version 2
    this.mockMvc
        .perform(
            createBuilderForPutWithJSONBody(
                apiKey,
                "/instruments/" + instrument.getId(),
                anyUser,
                "{ \"name\": \"version two instrument\" }"))
        .andExpect(status().isOk())
        .andReturn();

    // version 1 resolves to the historical snapshot
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/instruments/" + instrument.getId() + "/versions/1",
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiInstrument instrumentV1 = getFromJsonResponseBody(result, ApiInstrument.class);
    assertEquals("version one instrument", instrumentV1.getName());
    assertTrue(instrumentV1.isHistoricalVersion());
    assertEquals("IN" + instrument.getId() + "v1", instrumentV1.getGlobalId());
    assertTrue(instrumentV1.getPermittedActions().contains(ApiInventoryRecordPermittedAction.READ));

    // the current version resolves to the live record, not flagged historical
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/instruments/" + instrument.getId() + "/versions/2",
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiInstrument instrumentV2 = getFromJsonResponseBody(result, ApiInstrument.class);
    assertEquals("version two instrument", instrumentV2.getName());
    assertEquals(false, instrumentV2.isHistoricalVersion());

    // a version that never existed is a clean 404
    this.mockMvc
        .perform(
            createBuilderForGet(
                API_VERSION.ONE,
                apiKey,
                "/instruments/" + instrument.getId() + "/versions/99",
                anyUser))
        .andExpect(status().isNotFound())
        .andReturn();
  }

  @Test
  public void versionEndpointsDowngradeViewWithoutReadPermission() throws Exception {
    User owner = createInitAndLoginAnyUser();
    String ownerApiKey = createNewApiKeyForUser(owner);
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(owner);
    Long subSampleId = sample.getSubSamples().get(0).getId();
    ApiContainer container = createBasicContainerForUser(owner, "private container");
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    ownerApiKey, "/instruments", owner, "{ \"name\": \"private instrument\" }"))
            .andExpect(status().isCreated())
            .andReturn();
    ApiInstrument instrument = getFromJsonResponseBody(result, ApiInstrument.class);

    // bump everything to version 2 so that version 1 exercises the HISTORICAL snapshot
    // downgrade, not the live-record one
    this.mockMvc
        .perform(
            createBuilderForPutWithJSONBody(
                ownerApiKey, "/samples/" + sample.getId(), owner, "{ \"name\": \"sample v2\" }"))
        .andExpect(status().isOk());
    this.mockMvc
        .perform(
            createBuilderForPutWithJSONBody(
                ownerApiKey, "/subSamples/" + subSampleId, owner, "{ \"name\": \"subsample v2\" }"))
        .andExpect(status().isOk());
    this.mockMvc
        .perform(
            createBuilderForPutWithJSONBody(
                ownerApiKey,
                "/containers/" + container.getId(),
                owner,
                "{ \"name\": \"container v2\" }"))
        .andExpect(status().isOk());
    this.mockMvc
        .perform(
            createBuilderForPutWithJSONBody(
                ownerApiKey,
                "/instruments/" + instrument.getId(),
                owner,
                "{ \"name\": \"instrument v2\" }"))
        .andExpect(status().isOk());

    User otherUser = createInitAndLoginAnyUser();
    String otherApiKey = createNewApiKeyForUser(otherUser);

    // same outcome as reading the live record without permission: a cleared public view,
    // with no permitted actions and the sensitive properties stripped, for every type
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    otherApiKey,
                    "/samples/" + sample.getId() + "/versions/1",
                    otherUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiSample publicViewSample = getFromJsonResponseBody(result, ApiSample.class);
    assertTrue(publicViewSample.getPermittedActions().isEmpty());
    assertNull(publicViewSample.getFields());
    assertNull(publicViewSample.getExtraFields());

    result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    otherApiKey,
                    "/subSamples/" + subSampleId + "/versions/1",
                    otherUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiSubSample publicViewSubSample = getFromJsonResponseBody(result, ApiSubSample.class);
    assertTrue(publicViewSubSample.getPermittedActions().isEmpty());
    assertNull(publicViewSubSample.getNotes());
    assertNull(publicViewSubSample.getExtraFields());

    result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    otherApiKey,
                    "/containers/" + container.getId() + "/versions/1",
                    otherUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiContainer publicViewContainer = getFromJsonResponseBody(result, ApiContainer.class);
    assertTrue(publicViewContainer.getPermittedActions().isEmpty());
    assertNull(publicViewContainer.getAttachments());
    assertNull(publicViewContainer.getBarcodes());

    result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    otherApiKey,
                    "/instruments/" + instrument.getId() + "/versions/1",
                    otherUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiInstrument publicViewInstrument = getFromJsonResponseBody(result, ApiInstrument.class);
    assertTrue(publicViewInstrument.getPermittedActions().isEmpty());
    assertNull(publicViewInstrument.getAttachments());
    assertNull(publicViewInstrument.getBarcodes());
  }

  @Test
  public void sampleTemplateRevisionsListing() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    // create a template, then update it to bump it to version 2
    ApiSampleTemplatePost templatePost = new ApiSampleTemplatePost();
    templatePost.setName("template version one");
    templatePost.setDefaultUnitId(RSUnitDef.GRAM.getId());
    templatePost.setSampleSource(SampleSource.LAB_CREATED);
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/sampleTemplates", anyUser, templatePost))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    ApiSampleTemplate template = getFromJsonResponseBody(result, ApiSampleTemplate.class);

    this.mockMvc
        .perform(
            createBuilderForPutWithJSONBody(
                apiKey,
                "/sampleTemplates/" + template.getId(),
                anyUser,
                "{ \"name\": \"template version two\" }"))
        .andExpect(status().isOk())
        .andReturn();

    // both versions appear in the revisions history, oldest first
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/sampleTemplates/" + template.getId() + "/revisions",
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiInventoryRecordRevisionList history =
        getFromJsonResponseBody(result, ApiInventoryRecordRevisionList.class);
    assertEquals(2, history.getRevisions().size());
    assertEquals("template version one", history.getRevisions().get(0).getRecord().getName());
    assertEquals("template version two", history.getRevisions().get(1).getRecord().getName());

    // a plain sample id is not addressable through the template revisions endpoint
    ApiSampleWithFullSubSamples plainSample = createBasicSampleForUser(anyUser, "not a template");
    this.mockMvc
        .perform(
            createBuilderForGet(
                API_VERSION.ONE,
                apiKey,
                "/sampleTemplates/" + plainSample.getId() + "/revisions",
                anyUser))
        .andExpect(status().isNotFound())
        .andReturn();
  }
}
