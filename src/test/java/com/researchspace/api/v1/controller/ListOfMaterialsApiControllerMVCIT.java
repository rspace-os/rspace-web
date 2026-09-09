package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList;
import com.researchspace.api.v1.model.ApiListOfMaterials;
import com.researchspace.api.v1.model.ApiMaterialUsage;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.apiutils.ApiError;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.StructuredDocument;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

public class ListOfMaterialsApiControllerMVCIT extends API_MVC_InventoryTestBase {

  @BeforeEach
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void createEditDeleteListOfMaterialsForDocField() throws Exception {

    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    ApiSampleWithFullSubSamples mySample = createBasicSampleForUser(anyUser);
    ApiSubSample mySubSample = mySample.getSubSamples().get(0);
    assertEquals("5 g", mySubSample.getQuantity().toQuantityInfo().toPlainString());
    StructuredDocument myDoc = createBasicDocumentInRootFolderWithText(anyUser, "text");
    Field myField = myDoc.getFields().get(0);

    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/listOfMaterials/forDocument/" + myDoc.getId(),
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());
    List<ApiListOfMaterials> foundLists =
        mvcUtils.getFromJsonResponseBodyByTypeRef(result, new TypeReference<>() {});
    assertNotNull(foundLists);
    assertEquals(0, foundLists.size());

    // add list of materials
    String newListJson = "{ \"name\": \"my list\", \"elnFieldId\": " + myField.getId() + " } ";
    result =
        this.mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/listOfMaterials", anyUser, newListJson))
            .andExpect(status().isCreated())
            .andReturn();
    assertNull(result.getResolvedException());
    ApiListOfMaterials createdList =
        mvcUtils.getFromJsonResponseBody(result, ApiListOfMaterials.class);
    assertNotNull(createdList.getId());
    assertEquals("my list", createdList.getName());
    assertEquals(myField.getId(), createdList.getElnFieldId());
    assertEquals(myDoc.getGlobalIdentifier(), createdList.getElnDocument().getGlobalId());
    assertEquals(0, createdList.getMaterials().size());

    // update added list
    String listUpdateJson =
        "{ \"name\": \"my list (updated)\", \"materials\": [ "
            + " { \"invRec\": { \"id\": "
            + mySubSample.getId()
            + ", \"type\":\"SUBSAMPLE\" }, "
            + "   \"usedQuantity\": { \"numericValue\": \"1\", \"unitId\": 7},"
            + "   \"updateInventoryQuantity\": true } "
            + "] } ";
    result =
        this.mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/listOfMaterials/" + createdList.getId(), anyUser, listUpdateJson))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());
    ApiListOfMaterials updatedList =
        mvcUtils.getFromJsonResponseBody(result, ApiListOfMaterials.class);
    assertEquals("my list (updated)", updatedList.getName());
    assertEquals(myDoc.getGlobalIdentifier(), updatedList.getElnDocument().getGlobalId());
    assertEquals(1, updatedList.getMaterials().size());
    ApiMaterialUsage updatedUsage = updatedList.getMaterials().get(0);
    assertEquals("1 g", updatedUsage.getUsedQuantity().toQuantityInfo().toPlainString());
    // inventory quantity reduced ("updateInventoryQuantity": true flag)
    assertEquals("4 g", updatedUsage.getRecord().getQuantity().toQuantityInfo().toPlainString());

    // check lists attached to the field
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/listOfMaterials/forField/" + myField.getId(),
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());
    foundLists = mvcUtils.getFromJsonResponseBodyByTypeRef(result, new TypeReference<>() {});
    assertNotNull(foundLists);
    assertEquals(1, foundLists.size());

    // check lists attached to the inventory item
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/listOfMaterials/forInventoryItem/" + mySubSample.getGlobalId(),
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());
    foundLists = mvcUtils.getFromJsonResponseBodyByTypeRef(result, new TypeReference<>() {});
    assertNotNull(foundLists);
    assertEquals(1, foundLists.size());

    // delete added list
    result =
        mockMvc
            .perform(
                createBuilderForDelete(
                    apiKey, "/listOfMaterials/{id}", anyUser, createdList.getId()))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertNull(result.getResolvedException());

    // confirm list deleted
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/listOfMaterials/forField/" + myField.getId(),
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());
    foundLists = mvcUtils.getFromJsonResponseBodyByTypeRef(result, new TypeReference<>() {});
    assertNotNull(foundLists);
    assertEquals(0, foundLists.size());
  }

  @Test
  public void stockDecrementViaLomBumpsSubSampleVersionAndAddsRevision() throws Exception {
    // RSDEV-1318: an ELN-driven stock decrement bumps the version and adds a revision entry
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    ApiSampleWithFullSubSamples mySample = createBasicSampleForUser(anyUser);
    ApiSubSample mySubSample = mySample.getSubSamples().get(0);
    Long subSampleId = mySubSample.getId();
    assertEquals(1L, mySubSample.getVersion());
    StructuredDocument myDoc = createBasicDocumentInRootFolderWithText(anyUser, "text");
    Field myField = myDoc.getFields().get(0);

    // create a list of materials, then register a 1 g usage that reduces the inventory stock
    String newListJson = "{ \"name\": \"my list\", \"elnFieldId\": " + myField.getId() + " } ";
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/listOfMaterials", anyUser, newListJson))
            .andExpect(status().isCreated())
            .andReturn();
    assertNull(result.getResolvedException());
    ApiListOfMaterials createdList =
        mvcUtils.getFromJsonResponseBody(result, ApiListOfMaterials.class);
    String listUpdateJson =
        "{ \"materials\": [ "
            + " { \"invRec\": { \"id\": "
            + subSampleId
            + ", \"type\":\"SUBSAMPLE\" }, "
            + "   \"usedQuantity\": { \"numericValue\": \"1\", \"unitId\": 7},"
            + "   \"updateInventoryQuantity\": true } "
            + "] } ";
    result =
        this.mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/listOfMaterials/" + createdList.getId(), anyUser, listUpdateJson))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());

    // the subsample's stock is reduced AND its user-facing version is bumped
    ApiSubSample reloaded = getSubSample(apiKey, anyUser, subSampleId);
    assertEquals("4 g", reloaded.getQuantity().toQuantityInfo().toPlainString());
    assertEquals(2L, reloaded.getVersion(), "a stock decrement must bump the subsample version");

    // ... and the decrement is a new entry in the revision history (creation + decrement)
    ApiInventoryRecordRevisionList history = getRevisions(apiKey, anyUser, subSampleId);
    assertEquals(
        2, history.getRevisions().size(), "a stock decrement must add a revision-history entry");

    // the two revisions carry distinct versions 1 and 2, so each version resolves to a snapshot
    ApiSubSample revision1 = getRevisionSnapshot(apiKey, anyUser, subSampleId, history, 0);
    assertEquals(1L, revision1.getVersion());
    assertEquals("5 g", revision1.getQuantity().toQuantityInfo().toPlainString());
    ApiSubSample revision2 = getRevisionSnapshot(apiKey, anyUser, subSampleId, history, 1);
    assertEquals(2L, revision2.getVersion());
    assertEquals("4 g", revision2.getQuantity().toQuantityInfo().toPlainString());
  }

  @Test
  public void twoUsagesOfOneSubSampleInOneRequestBumpVersionOnce() throws Exception {
    // RSDEV-1319: Envers writes one revision per entity per transaction, so a request that
    // decrements the same subsample twice must still advance the version exactly once, otherwise
    // the skipped version has no revision and GET /subSamples/{id}/versions/{n} finds nothing
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    ApiSampleWithFullSubSamples mySample = createBasicSampleForUser(anyUser);
    Long subSampleId = mySample.getSubSamples().get(0).getId();
    assertEquals(1L, mySample.getSubSamples().get(0).getVersion());
    StructuredDocument myDoc = createBasicDocumentInRootFolderWithText(anyUser, "text");
    Field myField = myDoc.getFields().get(0);

    // one list of materials naming the same subsample twice, 1 g used by each entry
    createListOfMaterialsDeducting(apiKey, anyUser, myField.getId(), subSampleId, 2);

    // both usages come off the stock, but the version advances only once
    ApiSubSample reloaded = getSubSample(apiKey, anyUser, subSampleId);
    assertEquals("3 g", reloaded.getQuantity().toQuantityInfo().toPlainString());
    assertEquals(2L, reloaded.getVersion(), "two decrements in one transaction are one version");

    ApiInventoryRecordRevisionList history = getRevisions(apiKey, anyUser, subSampleId);
    assertEquals(2, history.getRevisions().size(), "one transaction writes one revision");
    ApiSubSample revision2 = getRevisionSnapshot(apiKey, anyUser, subSampleId, history, 1);
    assertEquals(2L, revision2.getVersion(), "the live version must resolve to a revision");
    assertEquals("3 g", revision2.getQuantity().toQuantityInfo().toPlainString());

    // a second request is a second transaction on the same thread: the guard must not persist
    // across it, or the version would silently stop advancing for this subsample
    createListOfMaterialsDeducting(apiKey, anyUser, myField.getId(), subSampleId, 1);

    reloaded = getSubSample(apiKey, anyUser, subSampleId);
    assertEquals("2 g", reloaded.getQuantity().toQuantityInfo().toPlainString());
    assertEquals(3L, reloaded.getVersion(), "a new transaction bumps the version again");

    history = getRevisions(apiKey, anyUser, subSampleId);
    assertEquals(3, history.getRevisions().size());
    ApiSubSample revision3 = getRevisionSnapshot(apiKey, anyUser, subSampleId, history, 2);
    assertEquals(3L, revision3.getVersion());
    assertEquals("2 g", revision3.getQuantity().toQuantityInfo().toPlainString());
  }

  @Test
  public void parallelListsDeductingSiblingSubSamplesKeepTheParentTotalExact() throws Exception {
    // List of Materials enters registerApiSubSampleUsage with no prior row lock, so it acquires
    // the parent's sibling-set lock first and the subsample's own row second, the same order as
    // the operations endpoint. Two concurrent lists deducting two siblings of ONE sample must
    // therefore both succeed: a non-201 is a deadlock victim or lock-wait timeout, and a parent
    // total that differs from the children's sum is the snapshot-sum bug (each transaction summing
    // the sibling ENTITIES as of its own snapshot, losing the other's decrement).
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    String subSampleJson = "{\"quantity\":{\"numericValue\":5,\"unitId\":7}}";
    String sampleJson =
        "{\"name\":\"lom siblings\",\"subSamples\":[" + subSampleJson + "," + subSampleJson + "]}";
    MvcResult sampleResult =
        mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/samples", anyUser, sampleJson))
            .andExpect(status().isCreated())
            .andReturn();
    ApiSampleWithFullSubSamples sample =
        mvcUtils.getFromJsonResponseBody(sampleResult, ApiSampleWithFullSubSamples.class);
    Long firstId = sample.getSubSamples().get(0).getId();
    Long secondId = sample.getSubSamples().get(1).getId();
    Long firstFieldId =
        createBasicDocumentInRootFolderWithText(anyUser, "lom parallel 1")
            .getFields()
            .get(0)
            .getId();
    Long secondFieldId =
        createBasicDocumentInRootFolderWithText(anyUser, "lom parallel 2")
            .getFields()
            .get(0)
            .getId();

    ExecutorService pool = Executors.newFixedThreadPool(2);
    List<Integer> statuses = new ArrayList<>();
    try {
      List<Callable<Integer>> posts =
          List.of(
              () -> postListOfMaterialsDeducting(apiKey, anyUser, firstFieldId, firstId),
              () -> postListOfMaterialsDeducting(apiKey, anyUser, secondFieldId, secondId));
      for (Future<Integer> future : pool.invokeAll(posts)) {
        statuses.add(future.get());
      }
    } finally {
      pool.shutdown();
    }

    assertTrue(
        statuses.stream().allMatch(s -> s == 201),
        () -> "both sibling deductions should succeed without deadlocking, got " + statuses);
    // each list took its 1 g off its own subsample...
    assertEquals(
        "4 g",
        getSubSample(apiKey, anyUser, firstId).getQuantity().toQuantityInfo().toPlainString());
    assertEquals(
        "4 g",
        getSubSample(apiKey, anyUser, secondId).getQuantity().toQuantityInfo().toPlainString());
    // ...and the denormalised parent total reflects BOTH decrements
    MvcResult sampleGet =
        mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/samples/" + sample.getId(), anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiSample reloaded = mvcUtils.getFromJsonResponseBody(sampleGet, ApiSample.class);
    assertEquals(
        0,
        new BigDecimal("8").compareTo(reloaded.getQuantity().getNumericValue()),
        () -> "parent total should be 8 g after two 1 g deductions, got " + reloaded.getQuantity());
  }

  @Test
  public void parallelListsExhaustingOneSubSampleNeverResurrectItsStock() throws Exception {
    // Both lists load the subsample at 5 g before either takes its row lock, and each asks for the
    // whole 5 g. The winner commits 0 g; the loser then reads 0 g as its locked scalar, so its
    // usage clamps to zero and it deducts nothing. Its cached entity still holds the stale 5 g,
    // though, so anything that dirties that instance makes Hibernate's full-row flush write 5 g
    // back and resurrect stock that was already used up (Codex review, PR #1090). The invariant is
    // the final quantity, not which request won.
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    MvcResult sampleResult =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey,
                    "/samples",
                    anyUser,
                    "{\"name\":\"lom"
                        + " exhaust\",\"subSamples\":[{\"quantity\":{\"numericValue\":5,\"unitId\":7}}]}"))
            .andExpect(status().isCreated())
            .andReturn();
    ApiSampleWithFullSubSamples sample =
        mvcUtils.getFromJsonResponseBody(sampleResult, ApiSampleWithFullSubSamples.class);
    Long subSampleId = sample.getSubSamples().get(0).getId();
    Long firstFieldId =
        createBasicDocumentInRootFolderWithText(anyUser, "lom exhaust 1")
            .getFields()
            .get(0)
            .getId();
    Long secondFieldId =
        createBasicDocumentInRootFolderWithText(anyUser, "lom exhaust 2")
            .getFields()
            .get(0)
            .getId();

    ExecutorService pool = Executors.newFixedThreadPool(2);
    List<Integer> statuses = new ArrayList<>();
    try {
      List<Callable<Integer>> posts =
          List.of(
              () -> postListOfMaterialsUsing(apiKey, anyUser, firstFieldId, subSampleId, "5"),
              () -> postListOfMaterialsUsing(apiKey, anyUser, secondFieldId, subSampleId, "5"));
      for (Future<Integer> future : pool.invokeAll(posts)) {
        statuses.add(future.get());
      }
    } finally {
      pool.shutdown();
    }

    assertTrue(
        statuses.stream().noneMatch(status -> status >= 500),
        () -> "no 5xx from two lists exhausting one subsample, got " + statuses);
    assertEquals(
        "0 g",
        getSubSample(apiKey, anyUser, subSampleId).getQuantity().toQuantityInfo().toPlainString(),
        () -> "the exhausted subsample must stay empty, got statuses " + statuses);
  }

  /** Posts a list of materials using the given amount in grams, returning the HTTP status. */
  private int postListOfMaterialsUsing(
      String apiKey, User user, Long elnFieldId, Long subSampleId, String grams) throws Exception {
    String usage =
        "{ \"invRec\": { \"id\": "
            + subSampleId
            + ", \"type\":\"SUBSAMPLE\" },"
            + " \"usedQuantity\": { \"numericValue\": \""
            + grams
            + "\", \"unitId\": 7},"
            + " \"updateInventoryQuantity\": true }";
    String newListJson =
        "{ \"name\": \"exhausting list\", \"elnFieldId\": "
            + elnFieldId
            + ", \"materials\": ["
            + usage
            + "] }";
    return mockMvc
        .perform(createBuilderForPostWithJSONBody(apiKey, "/listOfMaterials", user, newListJson))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  /** Posts a list of materials using 1 g of the given subsample, returning the HTTP status. */
  private int postListOfMaterialsDeducting(
      String apiKey, User user, Long elnFieldId, Long subSampleId) throws Exception {
    String usage =
        "{ \"invRec\": { \"id\": "
            + subSampleId
            + ", \"type\":\"SUBSAMPLE\" },"
            + " \"usedQuantity\": { \"numericValue\": \"1\", \"unitId\": 7},"
            + " \"updateInventoryQuantity\": true }";
    String newListJson =
        "{ \"name\": \"parallel list\", \"elnFieldId\": "
            + elnFieldId
            + ", \"materials\": ["
            + usage
            + "] }";
    return mockMvc
        .perform(createBuilderForPostWithJSONBody(apiKey, "/listOfMaterials", user, newListJson))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  /** Creates a list of materials using 1 g of the given subsample, repeated usageCount times. */
  private void createListOfMaterialsDeducting(
      String apiKey, User user, Long elnFieldId, Long subSampleId, int usageCount)
      throws Exception {
    String usage =
        "{ \"invRec\": { \"id\": "
            + subSampleId
            + ", \"type\":\"SUBSAMPLE\" },"
            + " \"usedQuantity\": { \"numericValue\": \"1\", \"unitId\": 7},"
            + " \"updateInventoryQuantity\": true }";
    String newListJson =
        "{ \"name\": \"my list\", \"elnFieldId\": "
            + elnFieldId
            + ", \"materials\": ["
            + String.join(",", Collections.nCopies(usageCount, usage))
            + "] }";
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/listOfMaterials", user, newListJson))
            .andExpect(status().isCreated())
            .andReturn();
    assertNull(result.getResolvedException());
  }

  private ApiSubSample getSubSample(String apiKey, User user, Long subSampleId) throws Exception {
    MvcResult result =
        this.mockMvc
            .perform(getSubSampleById(user, apiKey, subSampleId))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());
    return mvcUtils.getFromJsonResponseBody(result, ApiSubSample.class);
  }

  private ApiInventoryRecordRevisionList getRevisions(String apiKey, User user, Long subSampleId)
      throws Exception {
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE, apiKey, "/subSamples/" + subSampleId + "/revisions", user))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());
    return mvcUtils.getFromJsonResponseBody(result, ApiInventoryRecordRevisionList.class);
  }

  private ApiSubSample getRevisionSnapshot(
      String apiKey, User user, Long subSampleId, ApiInventoryRecordRevisionList history, int index)
      throws Exception {
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/subSamples/"
                        + subSampleId
                        + "/revisions/"
                        + history.getRevisions().get(index).getRevisionId(),
                    user))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());
    return mvcUtils.getFromJsonResponseBody(result, ApiSubSample.class);
  }

  @Test
  public void listOfMaterialsForInstrumentReturnsLinkedDocuments() throws Exception {

    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    ApiInstrument myInstrument = createBasicInstrumentForUser(anyUser);
    StructuredDocument myDoc = createBasicDocumentInRootFolderWithText(anyUser, "text");
    Field myField = myDoc.getFields().get(0);

    // link the instrument to the document field via a List of Materials (no quantity)
    createBasicListOfMaterialsForUserAndDocField(
        anyUser, myField, List.of(new ApiMaterialUsage(myInstrument, null)));

    // GET /listOfMaterials/forInventoryItem/{instrumentGlobalId}
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/listOfMaterials/forInventoryItem/" + myInstrument.getGlobalId(),
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());

    List<ApiListOfMaterials> foundLists =
        mvcUtils.getFromJsonResponseBodyByTypeRef(result, new TypeReference<>() {});
    assertNotNull(foundLists);
    assertEquals(1, foundLists.size());

    // the endpoint returns the list of linked documents: each LoM carries its ELN document
    ApiListOfMaterials lom = foundLists.get(0);
    assertNotNull(lom.getElnDocument());
    assertEquals(myDoc.getGlobalIdentifier(), lom.getElnDocument().getGlobalId());

    // and the linked material is the instrument we added
    assertEquals(1, lom.getMaterials().size());
    assertEquals(myInstrument.getGlobalId(), lom.getMaterials().get(0).getRecord().getGlobalId());
  }

  @Test
  public void checkValidationOnLomCreationAndUpdate() throws Exception {

    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    // ApiSampleFull mySample = createBasicSampleForUser(anyUser);
    StructuredDocument myDoc = createBasicDocumentInRootFolderWithText(anyUser, "text");
    Field myField = myDoc.getFields().get(0);

    // check create list errors
    String lomWithouNameJson = "{ \"elnFieldId\": " + myField.getId() + " } ";
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey, "/listOfMaterials", anyUser, lomWithouNameJson))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertNotNull(result.getResolvedException());
    ApiError error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertApiErrorContainsMessage(error, "List of materials: name cannot be empty");

    String lomWithTooLongNameJson =
        "{ \"elnFieldId\": "
            + myField.getId()
            + ", \"name\": \""
            + StringUtils.repeat("x", 256)
            + "\""
            + ", \"description\": \""
            + StringUtils.repeat("x", 256)
            + "\" } ";
    result =
        this.mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey, "/listOfMaterials", anyUser, lomWithTooLongNameJson))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertNotNull(result.getResolvedException());
    error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertApiErrorContainsMessage(error, "name: Name cannot be longer than 255 chars");
    assertApiErrorContainsMessage(
        error, "description: Description cannot be longer than 255 chars");

    // sanity check
    String lomWithLongNameJson =
        "{ \"elnFieldId\": "
            + myField.getId()
            + ", \"name\": \""
            + StringUtils.repeat("x", 255)
            + "\""
            + ", \"description\": \""
            + StringUtils.repeat("x", 255)
            + "\" } ";
    result =
        this.mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey, "/listOfMaterials", anyUser, lomWithLongNameJson))
            .andExpect(status().isCreated())
            .andReturn();
    ApiListOfMaterials savedList =
        mvcUtils.getFromJsonResponseBody(result, ApiListOfMaterials.class);
    assertEquals(255, savedList.getName().length());
    assertEquals(255, savedList.getDescription().length());
  }

  @Test
  public void checkLomPermissionErrors() throws Exception {

    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    User otherUser = createInitAndLoginAnyUser();
    String otherApiKey = createNewApiKeyForUser(otherUser);
    StructuredDocument basicDoc = createBasicDocumentInRootFolderWithText(otherUser, "test");
    Field basicDocField = basicDoc.getFields().get(0);
    ApiSampleWithFullSubSamples basicSample = createBasicSampleForUser(otherUser);
    ApiListOfMaterials basicLom =
        createBasicListOfMaterialsForUserAndDocField(
            otherUser,
            basicDoc.getFields().get(0),
            List.of(new ApiMaterialUsage(basicSample, null)));

    // not permitted document
    this.mockMvc
        .perform(
            createBuilderForGet(
                API_VERSION.ONE,
                apiKey,
                "/listOfMaterials/forDocument/" + basicDoc.getId(),
                anyUser))
        .andExpect(status().isNotFound())
        .andReturn();
    // not permitted field
    this.mockMvc
        .perform(
            createBuilderForGet(
                API_VERSION.ONE,
                apiKey,
                "/listOfMaterials/forField/" + basicDocField.getId(),
                anyUser))
        .andExpect(status().isNotFound())
        .andReturn();
    // not permitted item
    this.mockMvc
        .perform(
            createBuilderForGet(
                API_VERSION.ONE,
                apiKey,
                "/listOfMaterials/forInventoryItem/" + basicSample.getGlobalId(),
                anyUser))
        .andExpect(status().isNotFound())
        .andReturn();
    // not-existent lom
    this.mockMvc
        .perform(createBuilderForGet(API_VERSION.ONE, apiKey, "/listOfMaterials/12345", anyUser))
        .andExpect(status().isNotFound())
        .andReturn();
    // not permitted lom
    this.mockMvc
        .perform(
            createBuilderForGet(
                API_VERSION.ONE, apiKey, "/listOfMaterials/" + basicLom.getId(), anyUser))
        .andExpect(status().isNotFound())
        .andReturn();
    this.mockMvc
        .perform(
            createBuilderForGet(
                API_VERSION.ONE,
                apiKey,
                "/listOfMaterials/" + basicLom.getId() + "/canEdit",
                anyUser))
        .andExpect(status().isNotFound())
        .andReturn();

    // sanity-check (other user can access fine)
    this.mockMvc
        .perform(
            createBuilderForGet(
                API_VERSION.ONE, otherApiKey, "/listOfMaterials/" + basicLom.getId(), otherUser))
        .andExpect(status().is2xxSuccessful())
        .andReturn();
    this.mockMvc
        .perform(
            createBuilderForGet(
                API_VERSION.ONE,
                otherApiKey,
                "/listOfMaterials/" + basicLom.getId() + "/canEdit",
                otherUser))
        .andExpect(status().is2xxSuccessful())
        .andReturn();
  }
}
