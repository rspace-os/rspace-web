package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleField;
import com.researchspace.api.v1.model.ApiSampleField.ApiInventoryFieldDef;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.api.v1.model.ApiSubSampleInfo;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.List;
import org.junit.Test;

public class SamplesApiManagerIT extends RealTransactionSpringTestBase {

  @Test
  public void sampleDetailsIncludeSubSampleParent() throws Exception {

    User testUser = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithInitialisedContent(testUser);

    // create subcontainer that stores a subsample
    ApiContainer topContainer = createBasicContainerForUser(testUser);
    ApiContainer subContainer = new ApiContainer("subContainer", ContainerType.LIST);
    subContainer.setParentContainer(topContainer);
    subContainer = containerApiMgr.createNewApiContainer(subContainer, testUser);
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(testUser);
    ApiSubSample subSample = sample.getSubSamples().get(0);
    moveSubSampleIntoListContainer(subSample.getId(), subContainer.getId(), testUser);

    // get sample details, verify subsample parent/grandparent are present
    Sample dbSample = sampleApiMgr.getSampleById(sample.getId(), testUser);
    assertEquals(1, dbSample.getSubSamples().size());
    SubSample dbSubSample = dbSample.getSubSamples().get(0);
    assertEquals(subContainer.getId(), dbSubSample.getParentId());
    assertEquals(1, dbSubSample.getParentContainer().getContentCount());
    assertEquals(1, dbSubSample.getParentContainer().getParentContainer().getContentCount());

    // but shouldn't be able to access parent's locations at this point, that's lazy-initialized
    assertLazyInitializationExceptionThrown(
        () -> dbSubSample.getParentContainer().getLocations().size());

    // verify subsample can be mapped to ApiSubSample without extra db queries
    ApiSubSampleInfo infoWithParents = new ApiSubSampleInfo(dbSubSample);
    assertEquals(3, infoWithParents.getParentContainers().size());
    assertEquals("subContainer", infoWithParents.getParentContainers().get(0).getName());
    assertEquals(0, infoWithParents.getAttachments().size());
  }

  @Test
  public void getTemplateVersions() throws Exception {

    User testUser = createInitAndLoginAnyUser();

    // create example template
    ApiSampleTemplate createdTemplate = createSampleTemplateWithRadioAndNumericFields(testUser);
    assertEquals(1, createdTemplate.getVersion());
    assertEquals(2, createdTemplate.getFields().size());

    // retrieve the template
    ApiSample retrievedTemplate =
        sampleApiMgr.getApiSampleTemplateById(createdTemplate.getId(), testUser);
    assertEquals(2, retrievedTemplate.getFields().size());
    ApiSampleField retrievedTemplateRadioField = retrievedTemplate.getFields().get(0);

    // prepare & run simple template update (v2)
    ApiSampleTemplate templateUpdates = new ApiSampleTemplate();
    templateUpdates.setId(createdTemplate.getId());
    templateUpdates.setName("test template with radio v2");
    sampleApiMgr.updateApiSampleTemplate(templateUpdates, testUser);

    // prepare & run radio field update (v3)
    templateUpdates = new ApiSampleTemplate();
    templateUpdates.setId(createdTemplate.getId());
    templateUpdates.setName("test template with radio v3");
    // add a new option to radio field, and set it as a default
    ApiSampleField radioFieldUpdates = new ApiSampleField();
    radioFieldUpdates.setId(retrievedTemplateRadioField.getId());
    radioFieldUpdates.setName("updated radio");
    ApiInventoryFieldDef updatedRadioDef =
        new ApiInventoryFieldDef(List.of("r2", "r3", "r4"), false);
    radioFieldUpdates.setDefinition(updatedRadioDef);
    templateUpdates.getFields().add(radioFieldUpdates);
    // run the update
    sampleApiMgr.updateApiSampleTemplate(templateUpdates, testUser);

    // prepare & run another simple update (v4)
    templateUpdates = new ApiSampleTemplate();
    templateUpdates.setId(createdTemplate.getId());
    templateUpdates.setName("test template with radio v4");
    sampleApiMgr.updateApiSampleTemplate(templateUpdates, testUser);

    // now try retrieving all the versions
    ApiSampleTemplate currentTemplate =
        sampleApiMgr.getApiSampleTemplateById(createdTemplate.getId(), testUser);
    assertEquals(4, currentTemplate.getVersion());

    ApiSampleTemplate firstVersion =
        sampleApiMgr.getApiSampleTemplateVersion(createdTemplate.getId(), 1L, testUser);
    assertNotNull(firstVersion);
    assertEquals("test template with radio and number", firstVersion.getName());
    assertEquals(1, firstVersion.getVersion());
    assertNotNull(firstVersion.getRevisionId());
    assertTrue(firstVersion.isHistoricalVersion());
    assertEquals(2, firstVersion.getFields().size());
    retrievedTemplateRadioField = firstVersion.getFields().get(0);
    assertEquals(
        List.of("r1", "r2", "r3"), retrievedTemplateRadioField.getDefinition().getOptions());

    ApiSampleTemplate secondVersion =
        sampleApiMgr.getApiSampleTemplateVersion(createdTemplate.getId(), 2L, testUser);
    assertNotNull(secondVersion);
    assertEquals("test template with radio v2", secondVersion.getName());
    assertEquals(2, secondVersion.getVersion());
    assertNotNull(secondVersion.getRevisionId());
    assertTrue(secondVersion.isHistoricalVersion());
    retrievedTemplateRadioField = secondVersion.getFields().get(0);
    assertEquals(
        List.of("r1", "r2", "r3"), retrievedTemplateRadioField.getDefinition().getOptions());

    ApiSampleTemplate thirdVersion =
        sampleApiMgr.getApiSampleTemplateVersion(createdTemplate.getId(), 3L, testUser);
    assertNotNull(thirdVersion);
    assertEquals("test template with radio v3", thirdVersion.getName());
    assertEquals(3, thirdVersion.getVersion());
    assertNotNull(thirdVersion.getRevisionId());
    assertTrue(thirdVersion.isHistoricalVersion());
    retrievedTemplateRadioField = thirdVersion.getFields().get(0);
    assertEquals(
        List.of("r2", "r3", "r4"), retrievedTemplateRadioField.getDefinition().getOptions());

    // fourth version is the current (latest) one
    ApiSampleTemplate fourthVersion =
        sampleApiMgr.getApiSampleTemplateVersion(createdTemplate.getId(), 4L, testUser);
    assertNotNull(fourthVersion);
    assertEquals("test template with radio v4", fourthVersion.getName());
    assertEquals(4, fourthVersion.getVersion());
    assertNull(fourthVersion.getRevisionId());
    assertFalse(fourthVersion.isHistoricalVersion());
    retrievedTemplateRadioField = fourthVersion.getFields().get(0);
    assertEquals(
        List.of("r2", "r3", "r4"), retrievedTemplateRadioField.getDefinition().getOptions());

    // try retrieving non-existing 5th version
    ApiSampleTemplate fifthVersion =
        sampleApiMgr.getApiSampleTemplateVersion(createdTemplate.getId(), 5L, testUser);
    assertNull(fifthVersion);
  }
}
