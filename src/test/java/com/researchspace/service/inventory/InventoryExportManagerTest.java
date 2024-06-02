package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.Constants;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiContainerInfo;
import com.researchspace.api.v1.model.ApiListOfMaterials;
import com.researchspace.api.v1.model.ApiMaterialUsage;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.field.Field;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.impl.ContentInitializerForDevRunManager;
import com.researchspace.service.inventory.csvexport.CsvExportMode;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class InventoryExportManagerTest extends SpringTransactionalTest {

  @Autowired private InventoryExportManager exportMgr;

  @Test
  public void exportSelectedSubSamplesToCsv() throws IOException {
    User testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("sampleExport"));
    initialiseContentWithEmptyContent(testUser);
    ApiSubSample basicSubSample = createBasicSampleForUser(testUser).getSubSamples().get(0);
    ApiSubSample complexSubSample = createComplexSampleForUser(testUser).getSubSamples().get(0);

    String sampleAndSubSampleExport =
        exportMgr
            .exportSelectedItemsAsCsvContent(
                List.of(
                    new GlobalIdentifier(basicSubSample.getGlobalId()),
                    new GlobalIdentifier(complexSubSample.getGlobalId())),
                CsvExportMode.FULL,
                false,
                false,
                testUser)
            .getCombinedContent();

    // subsamples data
    assertTrue(
        sampleAndSubSampleExport.startsWith(
            "# RSpace Inventory Export\n# Exported content: SUBSAMPLES\n"),
        sampleAndSubSampleExport);
    String expectedSubSampleHeaderLine =
        "Global ID,Name,Tags,Owner,Description,Parent Sample (Global ID),Parent Container (Global"
            + " ID),Quantity,Notes,\"Data (TEXT, SS";
    assertTrue(
        sampleAndSubSampleExport.contains(expectedSubSampleHeaderLine), sampleAndSubSampleExport);
    assertTrue(sampleAndSubSampleExport.contains(",mySubSample,"), sampleAndSubSampleExport);
    assertTrue(sampleAndSubSampleExport.contains("test note"), sampleAndSubSampleExport);
  }

  @Test
  public void exportSelectedUserItemsToCsv() throws Exception {
    User testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("sampleExport"));
    initialiseContentWithExampleContent(testUser);
    // let's create additional content to include in export file
    ApiSampleWithFullSubSamples basicSample = createBasicSampleForUser(testUser);
    ApiSampleTemplate template = createSampleTemplateWithRadioAndNumericFields(testUser);
    ApiContainer gridContainer = createBasicGridContainerForUser(testUser, 4, 5);

    String containerAndSampleExport =
        exportMgr
            .exportSelectedItemsAsCsvContent(
                List.of(
                    new GlobalIdentifier(basicSample.getGlobalId()),
                    new GlobalIdentifier(template.getGlobalId()),
                    new GlobalIdentifier(gridContainer.getGlobalId())),
                CsvExportMode.FULL,
                false,
                true,
                testUser)
            .getCombinedContent();

    // starts with a container
    assertTrue(
        containerAndSampleExport.startsWith(
            "# RSpace Inventory Export\n# Exported content: CONTAINERS\n"),
        containerAndSampleExport);
    String expectedContainerHeaderLine =
        "Global ID,Name,Tags,Owner,Description,Parent Container (Global ID),"
            + "Container Type,Can Store Containers (Y/N),Can Store Subsamples (Y/N),"
            + "Number of Stored Containers,Number of Stored Subsamples";
    assertTrue(
        containerAndSampleExport.contains(expectedContainerHeaderLine), containerAndSampleExport);
    assertTrue(containerAndSampleExport.contains(",gridContainer,"), containerAndSampleExport);
    assertTrue(containerAndSampleExport.contains(",GRID(4x5),Y,Y,0,0"), containerAndSampleExport);

    // then a sample
    assertTrue(
        containerAndSampleExport.contains(
            "\n# RSpace Inventory Export\n# Exported content: SAMPLES\n"),
        containerAndSampleExport);
    String expectedSampleHeaderLine =
        "Global ID,Name,Tags,Owner,Description,Parent Template (Global ID),Parent Template"
            + " (name),Total Quantity,Expiry Date,Sample Source,Storage Temperature (min),Storage"
            + " Temperature (max)";
    assertTrue(
        containerAndSampleExport.contains(expectedSampleHeaderLine), containerAndSampleExport);
    assertTrue(containerAndSampleExport.contains(",mySample,,"), containerAndSampleExport);

    // then a template
    assertTrue(
        containerAndSampleExport.contains(
            "\n# RSpace Inventory Export\n# Exported content: SAMPLE_TEMPLATES\n"),
        containerAndSampleExport);
    String expectedTemplateHeaderLine =
        "Global ID,Name,Tags,Owner,Description,"
            + "Expiry Date,Sample Source,Storage Temperature (min),Storage Temperature (max)";
    assertTrue(
        containerAndSampleExport.contains(expectedSampleHeaderLine), containerAndSampleExport);
    assertTrue(
        containerAndSampleExport.contains(",test template with radio and number,"),
        containerAndSampleExport);

    // verify the output contains expected number of containers, samples and subsamples
    assertEquals(
        1, StringUtils.countMatches(containerAndSampleExport, "\nIC"), containerAndSampleExport);
    assertEquals(
        1, StringUtils.countMatches(containerAndSampleExport, "\nSA"), containerAndSampleExport);
    assertEquals(
        0, StringUtils.countMatches(containerAndSampleExport, "\nSS"), containerAndSampleExport);
    assertEquals(
        1, StringUtils.countMatches(containerAndSampleExport, "\nIT"), containerAndSampleExport);
  }

  @Test
  public void exportAllUserDefaultItemsToCsv() throws IOException {
    User testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("sampleExport"));
    initialiseContentWithExampleContent(testUser);
    createSampleTemplateWithMandatoryFields(testUser);

    // full export of all user's data
    String allUserItems =
        exportMgr
            .exportUserItemsAsCsvContent(
                List.of(testUser.getUsername()), CsvExportMode.FULL, true, testUser)
            .getCombinedContent();

    // starts with containers
    assertTrue(
        allUserItems.startsWith("# RSpace Inventory Export\n# Exported content: CONTAINERS\n"),
        allUserItems);
    String expectedContainerHeaderLine =
        "Global ID,Name,Tags,Owner,Description,Parent Container (Global ID),"
            + "Container Type,Can Store Containers (Y/N),Can Store Subsamples (Y/N),"
            + "Number of Stored Containers,Number of Stored Subsamples";
    assertTrue(allUserItems.contains(expectedContainerHeaderLine), allUserItems);
    assertTrue(allUserItems.contains(",storage shelf #1 (list container),"), allUserItems);
    assertTrue(allUserItems.contains(",4-drawer storage unit (image container),"), allUserItems);
    assertTrue(allUserItems.contains(",96-well plate (12x8 grid),"), allUserItems);

    // continues with samples
    assertTrue(
        allUserItems.contains("\n# RSpace Inventory Export\n# Exported content: SAMPLES\n"),
        allUserItems);
    String expectedSampleHeaderLine =
        "Global ID,Name,Tags,Owner,Description,Parent Template (Global ID),Parent Template"
            + " (name),Total Quantity,Expiry Date,Sample Source,Storage Temperature (min),Storage"
            + " Temperature (max),\"MyNumber (NUMBER, IT";
    assertTrue(allUserItems.contains(expectedSampleHeaderLine), allUserItems);
    assertTrue(allUserItems.contains("Complex Sample #1"), allUserItems);
    assertTrue(allUserItems.contains("Basic Sample"), allUserItems);

    // continues with subsamples
    assertTrue(
        allUserItems.contains("\n# RSpace Inventory Export\n# Exported content: SUBSAMPLES\n"),
        allUserItems);
    String expectedSubSampleHeaderLine =
        "Global ID,Name,Tags,Owner,Description,Parent Sample (Global ID),"
            + "Parent Container (Global ID),Quantity,Notes,\"My extra number (NUMBER, SS";
    assertTrue(allUserItems.contains(expectedSubSampleHeaderLine), allUserItems);
    assertTrue(allUserItems.contains("Complex Sample #1.01"), allUserItems);
    assertTrue(allUserItems.contains("Basic Sample.01"), allUserItems);

    // also includes sample templates
    assertTrue(
        allUserItems.contains(
            "\n# RSpace Inventory Export\n# Exported content: SAMPLE_TEMPLATES\n"),
        allUserItems);
    assertTrue(allUserItems.contains(",test template with mandatory text field,,"), allUserItems);

    // verify the output contains expected number of containers, samples and subsamples
    assertEquals(8, StringUtils.countMatches(allUserItems, "\nIC"), allUserItems);
    assertEquals(2, StringUtils.countMatches(allUserItems, "\nSA"), allUserItems);
    assertEquals(2, StringUtils.countMatches(allUserItems, "\nSS"), allUserItems);
    assertTrue(StringUtils.countMatches(allUserItems, "\nIT") > 0, allUserItems);
  }

  @Test
  public void exportContainerWithGroupContent() throws IOException {

    // create test user, test pi and a group
    User testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("sampleExportUser"));
    initialiseContentWithExampleContent(testUser);
    // create a pi and a group
    User pi =
        createAndSaveUserIfNotExists(
            getRandomAlphabeticString("sampleExportPi"), Constants.PI_ROLE);
    initialiseContentWithEmptyContent(pi);
    Group group = createGroup("group", pi);
    addUsersToGroup(pi, group, testUser);

    // find default box container of test user (contains a lot of content)
    ApiContainerInfo listContainer =
        containerApiMgr
            .getTopContainersForUser(
                PaginationCriteria.createDefaultForClass(Container.class), null, null, testUser)
            .getFirstResult();
    assertEquals(
        ContentInitializerForDevRunManager.EXAMPLE_TOP_LIST_CONTAINER_NAME,
        listContainer.getName());
    assertEquals(3, listContainer.getContentSummary().getTotalCount());
    // add a subsample belonging to pi user
    ApiSubSample piSubSample = createComplexSampleForUser(pi).getSubSamples().get(0);
    moveSubSampleIntoListContainer(piSubSample.getId(), listContainer.getId(), pi);

    // export just the box container, with content (full mode)
    String containerWithContent =
        exportMgr
            .exportSelectedItemsAsCsvContent(
                List.of(new GlobalIdentifier(listContainer.getGlobalId())),
                CsvExportMode.FULL,
                false,
                true,
                testUser)
            .getCombinedContent();
    // starts with containers
    assertTrue(
        containerWithContent.startsWith(
            "# RSpace Inventory Export\n# Exported content: CONTAINERS\n"),
        containerWithContent);
    assertTrue(
        containerWithContent.contains(",storage shelf #1 (list container),,sampleExportUser"),
        containerWithContent);
    assertTrue(
        containerWithContent.contains(",box #1 (list container),,sampleExportUser"),
        containerWithContent);
    assertTrue(
        containerWithContent.contains(",Basic Sample.01,,sampleExportUser"), containerWithContent);
    // includes pi's subsample
    assertTrue(containerWithContent.contains(",mySubSample,,sampleExportPi"), containerWithContent);

    // verify the output contains expected number of containers, samples and subsamples
    assertEquals(7, StringUtils.countMatches(containerWithContent, "\nIC"), containerWithContent);
    assertEquals(0, StringUtils.countMatches(containerWithContent, "\nSA"), containerWithContent);
    assertEquals(2, StringUtils.countMatches(containerWithContent, "\nSS"), containerWithContent);

    // export all data of testUser (compact mode)
    String allUserItems =
        exportMgr
            .exportUserItemsAsCsvContent(
                List.of(testUser.getUsername()), CsvExportMode.COMPACT, true, testUser)
            .getCombinedContent();
    // starts with containers
    assertTrue(
        allUserItems.startsWith("# RSpace Inventory Export\n# Exported content: CONTAINERS\n"),
        allUserItems);
    assertTrue(
        allUserItems.contains(",storage shelf #1 (list container),,sampleExportUser"),
        allUserItems);
    assertTrue(
        allUserItems.contains(",4-drawer storage unit (image container),,sampleExportUser"),
        allUserItems);
    // includes pi's subsample
    assertTrue(allUserItems.contains(",mySubSample,,sampleExportPi"), allUserItems);

    // verify the output contains expected number of containers, samples and subsamples
    assertEquals(8, StringUtils.countMatches(allUserItems, "\nIC"), allUserItems);
    assertEquals(2, StringUtils.countMatches(allUserItems, "\nSA"), allUserItems);
    assertEquals(3, StringUtils.countMatches(allUserItems, "\nSS"), allUserItems);
  }

  @Test
  public void exportContainerWithContentFromUnrelatedUser() throws IOException {

    // create test user, with a container holding subcontainer and subsample
    User testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("sampleExportUser"));
    initialiseContentWithEmptyContent(testUser);
    ApiContainer userContainer = createBasicContainerForUser(testUser, "userContainer");
    ApiContainer userSubContainer = createBasicContainerForUser(testUser, "userSubContainer");
    moveContainerIntoListContainer(userSubContainer.getId(), userContainer.getId(), testUser);
    ApiSampleWithFullSubSamples userSample = createBasicSampleForUser(testUser);
    moveSubSampleIntoListContainer(
        userSample.getSubSamples().get(0).getId(), userContainer.getId(), testUser);

    // create a pi with container storing two subcontainers
    User pi =
        createAndSaveUserIfNotExists(
            getRandomAlphabeticString("sampleExportPi"), Constants.PI_ROLE);
    initialiseContentWithEmptyContent(pi);
    ApiContainer piTopContainer = createBasicContainerForUser(pi, "piTopContainer");
    ApiContainer piSubContainer = createBasicContainerForUser(pi, "piSubContainer");
    ApiContainer piSubSubContainer = createBasicContainerForUser(pi, "piSubSubContainer");
    moveContainerIntoListContainer(piSubContainer.getId(), piTopContainer.getId(), pi);
    moveContainerIntoListContainer(piSubSubContainer.getId(), piTopContainer.getId(), pi);

    // create group
    Group group = createGroup("group", pi);
    addUsersToGroup(pi, group, testUser);

    // move container of a test user into pi's subcontainer
    moveContainerIntoListContainer(userContainer.getId(), piSubContainer.getId(), testUser);

    // remove user from group
    grpMgr.removeUserFromGroup(testUser.getUsername(), group.getId(), pi);

    // as a pi, try exporting container hierarchy starting from top container
    String containerWithContentPiExport =
        exportMgr
            .exportSelectedItemsAsCsvContent(
                List.of(new GlobalIdentifier(piTopContainer.getGlobalId())),
                CsvExportMode.FULL,
                false,
                true,
                pi)
            .getCombinedContent();
    // starts with containers
    assertTrue(
        containerWithContentPiExport.startsWith(
            "# RSpace Inventory Export\n# Exported content: CONTAINERS\n"),
        containerWithContentPiExport);
    assertTrue(
        containerWithContentPiExport.contains(",piTopContainer,,sampleExportPi"),
        containerWithContentPiExport);
    assertTrue(
        containerWithContentPiExport.contains(",piSubContainer,,sampleExportPi"),
        containerWithContentPiExport);
    assertTrue(
        containerWithContentPiExport.contains(",piSubSubContainer,,sampleExportPi"),
        containerWithContentPiExport);
    // pi has only limited read to user container, so won't be able to export it
    assertFalse(
        containerWithContentPiExport.contains(",userContainer,,sampleExportUser"),
        containerWithContentPiExport);
    // pi can no longer see subsample nor subcontainer
    assertFalse(
        containerWithContentPiExport.contains(",userSubContainer,,sampleExportUser"),
        containerWithContentPiExport);
    assertFalse(
        containerWithContentPiExport.contains(",mySubSample,,sampleExportUser"),
        containerWithContentPiExport);

    // verify the output contains expected number of containers, samples and subsamples
    assertEquals(
        3,
        StringUtils.countMatches(containerWithContentPiExport, "\nIC"),
        containerWithContentPiExport);
    assertEquals(
        0,
        StringUtils.countMatches(containerWithContentPiExport, "\nSA"),
        containerWithContentPiExport);
    assertEquals(
        0,
        StringUtils.countMatches(containerWithContentPiExport, "\nSS"),
        containerWithContentPiExport);

    // as a user, try exporting container hierarchy starting from piSubContainer
    // FIXME cannot start from top container yet, see RSINV-642
    String containerWithContentUserExport =
        exportMgr
            .exportSelectedItemsAsCsvContent(
                List.of(new GlobalIdentifier(piSubContainer.getGlobalId())),
                CsvExportMode.FULL,
                false,
                true,
                testUser)
            .getCombinedContent();
    // starts with containers
    assertTrue(
        containerWithContentUserExport.startsWith(
            "# RSpace Inventory Export\n# Exported content: CONTAINERS\n"),
        containerWithContentUserExport);
    assertTrue(
        containerWithContentUserExport.contains(",piSubContainer,,sampleExportPi"),
        containerWithContentUserExport);
    // contains user's container, subcontainer and subsample
    assertTrue(
        containerWithContentUserExport.contains(",userContainer,,sampleExportUser"),
        containerWithContentUserExport);
    assertTrue(
        containerWithContentUserExport.contains(",userSubContainer,,sampleExportUser"),
        containerWithContentUserExport);
    assertTrue(
        containerWithContentUserExport.contains(",mySubSample,,sampleExportUser"),
        containerWithContentUserExport);
    // doesn't contain pi's subsubcontainer, which user can no longer see
    assertFalse(
        containerWithContentUserExport.contains(",piSubContainer2,,sampleExportPi"),
        containerWithContentUserExport);

    // verify the output contains expected number of containers, samples and subsamples
    assertEquals(
        3,
        StringUtils.countMatches(containerWithContentUserExport, "\nIC"),
        containerWithContentUserExport);
    assertEquals(
        0,
        StringUtils.countMatches(containerWithContentUserExport, "\nSA"),
        containerWithContentUserExport);
    assertEquals(
        1,
        StringUtils.countMatches(containerWithContentUserExport, "\nSS"),
        containerWithContentUserExport);
  }

  @Test
  public void checkPermissionToContentRequestedForExport() throws IOException {

    User testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("export1"));
    ApiSampleWithFullSubSamples testUserBasicSample = createBasicSampleForUser(testUser);
    User otherUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("export2"));
    ApiSampleWithFullSubSamples otherUserBasicSample = createBasicSampleForUser(otherUser);

    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                exportMgr.exportUserItemsAsCsvContent(
                    List.of(testUser.getUsername(), otherUser.getUsername(), "unexisting"),
                    CsvExportMode.FULL,
                    true,
                    testUser));
    assertEquals(
        "Cannot export data of users ["
            + otherUser.getUsername()
            + ", unexisting] - users not found, or no permission",
        iae.getMessage());

    iae =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                exportMgr.exportSelectedItemsAsCsvContent(
                    List.of(
                        new GlobalIdentifier(testUserBasicSample.getGlobalId()),
                        new GlobalIdentifier(otherUserBasicSample.getGlobalId()),
                        new GlobalIdentifier("IT0")),
                    CsvExportMode.FULL,
                    false,
                    false,
                    testUser));
    assertEquals(
        "Cannot export items with global id ["
            + otherUserBasicSample.getGlobalId()
            + ", IT0] - items not found, or no permission",
        iae.getMessage());
  }

  @Test
  public void exportListOfMaterials() throws IOException {

    // create user and basic samples
    User testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("sampleExport"));
    initialiseContentWithEmptyContent(testUser);
    ApiSampleWithFullSubSamples basicSample = createBasicSampleForUser(testUser);
    ApiSubSample complexSubSample = createComplexSampleForUser(testUser).getSubSamples().get(0);

    StructuredDocument basicDoc = createBasicDocumentInRootFolderWithText(testUser, "test");
    Field basicDocField = basicDoc.getFields().get(0);

    // create LoM with sample and subsample
    ApiMaterialUsage sampleUsage = new ApiMaterialUsage(basicSample, null);
    ApiMaterialUsage subsampleUsage =
        new ApiMaterialUsage(
            complexSubSample, new ApiQuantityInfo(BigDecimal.ONE, RSUnitDef.MILLI_LITRE));
    ApiListOfMaterials createdLom =
        createBasicListOfMaterialsForUserAndDocField(
            testUser, basicDocField, List.of(sampleUsage, subsampleUsage));

    String sampleAndSubSampleExport =
        exportMgr
            .exportSelectedItemsAsCsvContent(
                List.of(new GlobalIdentifier(createdLom.getGlobalId())),
                CsvExportMode.FULL,
                false,
                false,
                testUser)
            .getCombinedContent();

    // subsamples data
    assertTrue(
        sampleAndSubSampleExport.startsWith(
            "# RSpace Inventory Export\n# Exported content: LIST_OF_MATERIALS\n"),
        sampleAndSubSampleExport);
    String expectedLomHeaderLine =
        "List of Materials (Global ID),List of Materials (Name),Used Material (Global ID),Used"
            + " Material (Name),Used Material (Type),Used Quantity\n";
    assertTrue(sampleAndSubSampleExport.contains(expectedLomHeaderLine), sampleAndSubSampleExport);
    assertTrue(sampleAndSubSampleExport.contains(",mySample,SAMPLE"), sampleAndSubSampleExport);
    assertTrue(
        sampleAndSubSampleExport.contains(",mySubSample,SUBSAMPLE,1 ml"), sampleAndSubSampleExport);
  }
}
