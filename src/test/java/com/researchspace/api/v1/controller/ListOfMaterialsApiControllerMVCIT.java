package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.researchspace.api.v1.model.ApiListOfMaterials;
import com.researchspace.api.v1.model.ApiMaterialUsage;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.apiutils.ApiError;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.StructuredDocument;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.web.servlet.MvcResult;

public class ListOfMaterialsApiControllerMVCIT extends API_MVC_InventoryTestBase {

  @Before
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
                createBuilderForPostWithJSONBody(apiKey, "/listOfMaterials/", anyUser, newListJson))
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
                    apiKey, "/listOfMaterials/", anyUser, lomWithouNameJson))
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
                    apiKey, "/listOfMaterials/", anyUser, lomWithTooLongNameJson))
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
                    apiKey, "/listOfMaterials/", anyUser, lomWithLongNameJson))
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
