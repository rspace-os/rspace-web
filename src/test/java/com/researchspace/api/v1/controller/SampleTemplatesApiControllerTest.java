package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.*;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult.InventoryBulkOperationStatus;
import com.researchspace.api.v1.model.ApiSampleField.ApiInventoryFieldDef;
import com.researchspace.model.User;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.impl.ContentInitializerForDevRunManager;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;

public class SampleTemplatesApiControllerTest extends SpringTransactionalTest {

  private @Autowired SampleTemplatesApiController templatesApi;

  private BindingResult mockBindingResult = mock(BindingResult.class);
  private User testUser;

  @Before
  public void setUp() {
    testUser = createInitAndLoginAnyUser();
    assertTrue(testUser.isContentInitialized());
    when(mockBindingResult.hasErrors()).thenReturn(false);
  }

  @Test
  public void retrievePaginatedTemplatesList() throws BindException {
    // no pagination parameters
    InventoryApiPaginationCriteria apiPgCrit = null;
    ApiSampleTemplateSearchResult foundTemplates =
        templatesApi.getTemplatesForUser(apiPgCrit, null, mockBindingResult, testUser);
    assertTrue(foundTemplates.getTotalHits().intValue() > 1);
    assertTrue(foundTemplates.getTemplates().size() > 1);
    assertEquals(1, foundTemplates.getLinks().size());

    // check details of a first template
    Optional<ApiSampleTemplateInfo> complexTemplateInfoOpt =
        foundTemplates.getTemplates().stream()
            .filter(
                st ->
                    ContentInitializerForDevRunManager.COMPLEX_SAMPLE_TEMPLATE_NAME.equals(
                        st.getName()))
            .findFirst();
    assertTrue(complexTemplateInfoOpt.isPresent());
    ApiSampleTemplateInfo complexTemplateInfo = complexTemplateInfoOpt.get();
    assertEquals(
        ContentInitializerForDevRunManager.COMPLEX_SAMPLE_TEMPLATE_NAME,
        complexTemplateInfo.getName());
    assertEquals("aliquot", complexTemplateInfo.getSubSampleAlias().getAlias());
    assertNotNull(complexTemplateInfo.getModifiedByFullName());
    assertEquals(4, complexTemplateInfo.getLinks().size()); // self + 3 images

    // pagination, second page
    apiPgCrit = new InventoryApiPaginationCriteria(1, 1, null);
    ApiSampleTemplateSearchResult paginatedTemplates =
        templatesApi.getTemplatesForUser(apiPgCrit, null, mockBindingResult, testUser);
    assertTrue(paginatedTemplates.getTotalHits().intValue() > 1);
    assertEquals(1, paginatedTemplates.getTemplates().size());
    assertTrue(paginatedTemplates.getLinks().size() > 1);
  }

  @Test
  public void checkNewTemplateFieldValidation() {
    ApiSampleTemplatePost sampleTemplatePost = new ApiSampleTemplatePost();
    sampleTemplatePost.setName("test template");
    // valid string field
    sampleTemplatePost
        .getFields()
        .add(createBasicApiSampleField("My String", ApiFieldType.STRING, "my string"));
    // invalid numeric field (wrong value)
    sampleTemplatePost
        .getFields()
        .add(createBasicApiSampleField("My Number", ApiFieldType.NUMBER, "3.14asdf"));
    // invalid string field (empty name)
    sampleTemplatePost
        .getFields()
        .add(createBasicApiSampleField("", ApiFieldType.TEXT, "my description"));
    // invalid string field (restricted name)
    sampleTemplatePost
        .getFields()
        .add(createBasicApiSampleField("Description", ApiFieldType.TEXT, "my description"));
    // invalid string field (name too long)
    sampleTemplatePost
        .getFields()
        .add(createBasicApiSampleField("abc".repeat(20), ApiFieldType.TEXT, "my text"));
    // invalid field (no type)
    sampleTemplatePost.getFields().add(createBasicApiSampleField("My Text", null, "my text"));

    BindingResult bindingResult = new BeanPropertyBindingResult(sampleTemplatePost, "templatePost");
    BindException be =
        assertThrows(
            BindException.class,
            () ->
                templatesApi.createNewSampleTemplate(sampleTemplatePost, bindingResult, testUser));
    assertEquals(5, be.getErrorCount());
    assertEquals(
        "errors.inventory.template.invalid.field.content", be.getAllErrors().get(0).getCode());
    assertEquals("errors.inventory.template.empty.field.name", be.getAllErrors().get(1).getCode());
    assertEquals(
        "errors.inventory.template.reserved.field.name", be.getAllErrors().get(2).getCode());
    assertEquals(
        "errors.inventory.template.field.name.too.long", be.getAllErrors().get(3).getCode());
    assertEquals("errors.inventory.template.empty.field.type", be.getAllErrors().get(4).getCode());
  }

  @Test
  public void checkUpdateTemplateFieldValidation() throws BindException {

    // create a template with 2 fields
    ApiSampleTemplatePost templatePost = new ApiSampleTemplatePost();
    templatePost.setName("test template");
    ApiSampleField stringField =
        createBasicApiSampleField("my string", ApiFieldType.STRING, "my string");
    templatePost.getFields().add(stringField);
    ApiSampleField numberField =
        createBasicApiSampleField("my number", ApiFieldType.NUMBER, "3.14");
    templatePost.getFields().add(numberField);

    BindingResult postBindingResult = new BeanPropertyBindingResult(templatePost, "templatePost");
    ApiSampleTemplate createdTemplate =
        templatesApi.createNewSampleTemplate(templatePost, postBindingResult, testUser);
    assertFalse(postBindingResult.hasErrors());
    assertNotNull(createdTemplate);
    assertTrue(createdTemplate.isTemplate());
    assertEquals("test template", createdTemplate.getName());
    Long createdTemplateId = createdTemplate.getId();
    assertNotNull(createdTemplateId);
    assertEquals(2, createdTemplate.getFields().size());

    // prepare update request
    ApiSampleTemplate templateUpdate = new ApiSampleTemplate();
    templateUpdate.setName("updated template");
    // string field update - invalid name (too long)
    ApiSampleField stringFieldUpdate = new ApiSampleField();
    stringFieldUpdate.setName("Description");
    stringFieldUpdate.setId(createdTemplate.getFields().get(0).getId());
    templateUpdate.getFields().add(stringFieldUpdate);
    // numeric field update - invalid name (restricted) and content
    ApiSampleField numericFieldUpdate = new ApiSampleField();
    numericFieldUpdate.setId(createdTemplate.getFields().get(1).getId());
    numericFieldUpdate.setName("abc".repeat(20));
    numericFieldUpdate.setType(
        ApiFieldType.NUMBER); // to trigger controller-level validation type must be present
    numericFieldUpdate.setContent("three point fifteen");
    templateUpdate.getFields().add(numericFieldUpdate);
    // attempt to add new field, but without name
    ApiSampleField newFieldUpdate = createBasicApiSampleField("", ApiFieldType.TEXT, "my text");
    newFieldUpdate.setNewFieldRequest(true);
    templateUpdate.getFields().add(newFieldUpdate);

    // try running the update
    BindingResult putBindingResult = new BeanPropertyBindingResult(templateUpdate, "templatePut");
    BindException be =
        assertThrows(
            BindException.class,
            () ->
                templatesApi.updateSampleTemplate(
                    createdTemplateId, templateUpdate, putBindingResult, testUser));
    assertEquals(4, be.getErrorCount());
    assertEquals(
        "errors.inventory.template.reserved.field.name", be.getAllErrors().get(0).getCode());
    assertEquals(
        "errors.inventory.template.field.name.too.long", be.getAllErrors().get(1).getCode());
    assertEquals(
        "errors.inventory.template.invalid.field.content", be.getAllErrors().get(2).getCode());
    assertEquals("errors.inventory.template.empty.field.name", be.getAllErrors().get(3).getCode());
  }

  @Test
  public void createEditSampleTemplate() throws BindException {

    // create a template with 2 fields
    ApiSampleTemplatePost sampleTemplatePost = new ApiSampleTemplatePost();
    sampleTemplatePost.setName("test template");
    sampleTemplatePost.setSubSampleAlias(new ApiSubSampleAlias("portion", "portions"));
    sampleTemplatePost.setDefaultUnitId(RSUnitDef.GRAM.getId());
    ApiSampleField radioField =
        createBasicApiSampleOptionsField("my radio", ApiFieldType.RADIO, List.of("2"));
    ApiInventoryFieldDef radioDef = new ApiInventoryFieldDef(List.of("1", "2", "3"), false);
    radioField.setDefinition(radioDef);
    sampleTemplatePost.getFields().add(radioField);
    ApiSampleField stringField =
        createBasicApiSampleField("my string", ApiFieldType.STRING, "my string");
    sampleTemplatePost.getFields().add(stringField);
    // set explicit ordering so string field is first
    stringField.setColumnIndex(1);
    radioField.setColumnIndex(2);

    BindingResult bindingResult = new BeanPropertyBindingResult(sampleTemplatePost, "templatePost");
    ApiSampleTemplate createdTemplate =
        templatesApi.createNewSampleTemplate(sampleTemplatePost, bindingResult, testUser);
    assertNotNull(createdTemplate);
    assertTrue(createdTemplate.isTemplate());
    assertEquals("test template", createdTemplate.getName());
    assertEquals("portion", createdTemplate.getSubSampleAlias().getAlias());
    assertEquals(RSUnitDef.GRAM.getId(), createdTemplate.getDefaultUnitId());
    assertEquals(2, createdTemplate.getFields().size());
    ApiSampleField firstField = createdTemplate.getFields().get(0);
    assertEquals("my string", firstField.getName());
    ApiSampleField secondField = createdTemplate.getFields().get(1);
    assertEquals("my radio", secondField.getName());
    assertEquals(null, secondField.getContent());
    assertEquals(List.of("2"), secondField.getSelectedOptions());
    assertEquals(List.of("1", "2", "3"), secondField.getDefinition().getOptions());

    // update the template: delete first field, update radio options of the second, add a third
    ApiSampleTemplate templateUpdates = new ApiSampleTemplate();
    ApiSampleField deleteFieldUpdate = new ApiSampleField();
    deleteFieldUpdate.setId(firstField.getId());
    deleteFieldUpdate.setDeleteFieldRequest(true);
    templateUpdates.getFields().add(deleteFieldUpdate);
    ApiSampleField radioUpdate = new ApiSampleField();
    radioUpdate.setId(secondField.getId());
    ApiInventoryFieldDef radioDefUpd = new ApiInventoryFieldDef(List.of("2", "3", "4"), false);
    radioUpdate.setDefinition(radioDefUpd);
    radioUpdate.setColumnIndex(2); // let radio field still be 2nd
    templateUpdates.getFields().add(radioUpdate);
    ApiSampleField createFieldUpdate =
        createBasicApiSampleField("my number", ApiFieldType.NUMBER, "-3.14");
    createFieldUpdate.setNewFieldRequest(true);
    createFieldUpdate.setColumnIndex(1); // let number field be 1st
    templateUpdates.getFields().add(createFieldUpdate);

    bindingResult = new BeanPropertyBindingResult(templateUpdates, "templatePut");
    ApiSampleTemplate updatedTemplate =
        templatesApi.updateSampleTemplate(
            createdTemplate.getId(), templateUpdates, bindingResult, testUser);
    assertNotNull(updatedTemplate);
    assertEquals(2, updatedTemplate.getFields().size());
    firstField = updatedTemplate.getFields().get(0);
    assertEquals("my number", firstField.getName());
    secondField = updatedTemplate.getFields().get(1);
    assertEquals("my radio", secondField.getName());
    assertEquals(null, secondField.getContent());
    assertEquals(List.of("2"), secondField.getSelectedOptions());
    assertEquals(List.of("2", "3", "4"), secondField.getDefinition().getOptions());
  }

  @Test
  public void updateAllSamplesForModifiedTemplate() throws Exception {

    // create a couple of samples for user
    createBasicSampleForUser(testUser);
    createComplexSampleForUser(testUser);

    // create new template
    ApiSampleTemplate createdTemplate = createSampleTemplateWithRadioAndNumericFields(testUser);
    assertEquals(2, createdTemplate.getFields().size());

    // create a couple of samples from the template
    ApiSampleWithFullSubSamples apiSample1 =
        new ApiSampleWithFullSubSamples("sample1 from template v1");
    apiSample1.setTemplateId(createdTemplate.getId());
    ApiSampleWithFullSubSamples createdSample1 =
        sampleApiMgr.createNewApiSample(apiSample1, testUser);
    assertNotNull(createdSample1);
    ApiSampleWithFullSubSamples apiSample2 =
        new ApiSampleWithFullSubSamples("sample2 from template v1");
    apiSample2.setTemplateId(createdTemplate.getId());
    ApiSampleWithFullSubSamples createdSample2 =
        sampleApiMgr.createNewApiSample(apiSample2, testUser);
    assertNotNull(createdSample2);

    // update the template
    ApiSampleTemplate templateUpdates = new ApiSampleTemplate();
    templateUpdates.setId(createdTemplate.getId());
    templateUpdates.setName("test template updated");
    // add a new text field
    ApiSampleField newTextField =
        createBasicApiSampleField("my text", ApiFieldType.TEXT, "default text");
    newTextField.setNewFieldRequest(true);
    newTextField.setColumnIndex(1); // make the new field the first one
    templateUpdates.getFields().add(newTextField);
    // add a new option to radio field
    ApiSampleField radioFieldUpdates = new ApiSampleField();
    radioFieldUpdates.setId(createdTemplate.getFields().get(0).getId());
    radioFieldUpdates.setName("updated radio");
    radioFieldUpdates.setColumnIndex(2); // move the old field to 2nd place
    ApiInventoryFieldDef updatedRadioDef =
        new ApiInventoryFieldDef(List.of("r2", "r3", "r4"), false);
    radioFieldUpdates.setDefinition(updatedRadioDef);
    templateUpdates.getFields().add(radioFieldUpdates);

    // update the template
    ApiSampleTemplate updatedTemplate =
        sampleApiMgr.updateApiSampleTemplate(templateUpdates, testUser);
    assertNotNull(updatedTemplate);
    assertEquals(2, updatedTemplate.getVersion());
    assertEquals(3, updatedTemplate.getFields().size());
    assertEquals("my text", updatedTemplate.getFields().get(0).getName());

    // retrieve samples, should point to previous version
    ApiSample retrievedSample1 = sampleApiMgr.getApiSampleById(createdSample1.getId(), testUser);
    assertEquals(createdTemplate.getId(), retrievedSample1.getTemplateId());
    assertEquals(1, retrievedSample1.getTemplateVersion());
    assertEquals(2, retrievedSample1.getFields().size());
    ApiSample retrievedSample2 = sampleApiMgr.getApiSampleById(createdSample2.getId(), testUser);
    assertEquals(createdTemplate.getId(), retrievedSample2.getTemplateId());
    assertEquals(1, retrievedSample2.getTemplateVersion());
    assertEquals(2, retrievedSample2.getFields().size());

    // run all-samples update
    ApiInventoryBulkOperationResult updateResult =
        templatesApi.updateSamplesToLatestTemplateVersion(createdTemplate.getId(), testUser);
    assertEquals(InventoryBulkOperationStatus.COMPLETED, updateResult.getStatus());
    assertEquals(2, updateResult.getSuccessCount());

    // retrieve samples, should point to previous version
    retrievedSample1 = sampleApiMgr.getApiSampleById(createdSample1.getId(), testUser);
    assertEquals(createdTemplate.getId(), retrievedSample1.getTemplateId());
    assertEquals(2, retrievedSample1.getTemplateVersion());
    assertEquals(3, retrievedSample1.getFields().size());
    assertEquals("my text", retrievedSample1.getFields().get(0).getName());
    retrievedSample2 = sampleApiMgr.getApiSampleById(createdSample2.getId(), testUser);
    assertEquals(createdTemplate.getId(), retrievedSample2.getTemplateId());
    assertEquals(2, retrievedSample2.getTemplateVersion());
    assertEquals(3, retrievedSample2.getFields().size());
    assertEquals("my text", retrievedSample2.getFields().get(0).getName());

    // run all-samples update again
    updateResult =
        templatesApi.updateSamplesToLatestTemplateVersion(createdTemplate.getId(), testUser);
    assertEquals(InventoryBulkOperationStatus.COMPLETED, updateResult.getStatus());
    assertEquals(0, updateResult.getSuccessCount()); // shouldn't find any more samples to update
  }
}
