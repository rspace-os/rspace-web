package com.researchspace.service.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.Constants;
import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiContainerInfo;
import com.researchspace.api.v1.model.ApiContainerLocation;
import com.researchspace.api.v1.model.ApiInstrument;
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
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
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
    assertThat(sampleAndSubSampleExport)
        .as(sampleAndSubSampleExport)
        .startsWith("# RSpace Inventory Export\n# Exported content: SUBSAMPLES\n");
    String expectedSubSampleHeaderLine =
        "Global ID,Name,Tags,Owner,Description,Parent Sample (Global ID),Parent Container (Global"
            + " ID),Quantity,Notes,\"Data (TEXT, SS";
    assertThat(sampleAndSubSampleExport)
        .as(sampleAndSubSampleExport)
        .contains(expectedSubSampleHeaderLine);
    assertThat(sampleAndSubSampleExport).as(sampleAndSubSampleExport).contains(",mySubSample,");
    assertThat(sampleAndSubSampleExport).as(sampleAndSubSampleExport).contains("test note");
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
    assertThat(containerAndSampleExport)
        .as(containerAndSampleExport)
        .startsWith("# RSpace Inventory Export\n# Exported content: CONTAINERS\n");
    String expectedContainerHeaderLine =
        "Global ID,Name,Tags,Owner,Description,Parent Container (Global ID),"
            + "Container Type,Can Store Containers (Y/N),Can Store Subsamples (Y/N),"
            + "Number of Stored Containers,Number of Stored Subsamples";
    assertThat(containerAndSampleExport)
        .as(containerAndSampleExport)
        .contains(expectedContainerHeaderLine);
    assertThat(containerAndSampleExport).as(containerAndSampleExport).contains(",gridContainer,");
    assertThat(containerAndSampleExport)
        .as(containerAndSampleExport)
        .contains(",GRID(4x5),Y,Y,0,0");

    // then a sample
    assertThat(containerAndSampleExport)
        .as(containerAndSampleExport)
        .contains("\n# RSpace Inventory Export\n# Exported content: SAMPLES\n");
    String expectedSampleHeaderLine =
        "Global ID,Name,Tags,Owner,Description,Parent Template (Global ID),Parent Template"
            + " (name),Total Quantity,Expiry Date,Sample Source,Storage Temperature (min),Storage"
            + " Temperature (max)";
    assertThat(containerAndSampleExport)
        .as(containerAndSampleExport)
        .contains(expectedSampleHeaderLine);
    assertThat(containerAndSampleExport).as(containerAndSampleExport).contains(",mySample,,");

    // then a template
    assertThat(containerAndSampleExport)
        .as(containerAndSampleExport)
        .contains("\n# RSpace Inventory Export\n# Exported content: SAMPLE_TEMPLATES\n");
    String expectedTemplateHeaderLine =
        "Global ID,Name,Tags,Owner,Description,"
            + "Expiry Date,Sample Source,Storage Temperature (min),Storage Temperature (max)";
    assertThat(containerAndSampleExport)
        .as(containerAndSampleExport)
        .contains(expectedSampleHeaderLine);
    assertThat(containerAndSampleExport)
        .as(containerAndSampleExport)
        .contains(",test template with radio and number,");

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
    ApiInstrument userInstrument = createBasicInstrumentForUser(testUser, "myUserInstrument");

    // full export of all user's data
    String allUserItems =
        exportMgr
            .exportUserItemsAsCsvContent(
                List.of(testUser.getUsername()), CsvExportMode.FULL, true, testUser)
            .getCombinedContent();

    // starts with containers
    assertThat(allUserItems)
        .as(allUserItems)
        .startsWith("# RSpace Inventory Export\n# Exported content: CONTAINERS\n");
    String expectedContainerHeaderLine =
        "Global ID,Name,Tags,Owner,Description,Parent Container (Global ID),"
            + "Container Type,Can Store Containers (Y/N),Can Store Subsamples (Y/N),"
            + "Number of Stored Containers,Number of Stored Subsamples";
    assertThat(allUserItems).as(allUserItems).contains(expectedContainerHeaderLine);
    assertThat(allUserItems).as(allUserItems).contains(",storage shelf #1 (list container),");
    assertThat(allUserItems).as(allUserItems).contains(",4-drawer storage unit (image container),");
    assertThat(allUserItems).as(allUserItems).contains(",96-well plate (12x8 grid),");

    // continues with samples
    assertThat(allUserItems)
        .as(allUserItems)
        .contains("\n# RSpace Inventory Export\n# Exported content: SAMPLES\n");
    String expectedSampleHeaderLine =
        "Global ID,Name,Tags,Owner,Description,Parent Template (Global ID),Parent Template"
            + " (name),Total Quantity,Expiry Date,Sample Source,Storage Temperature (min),Storage"
            + " Temperature (max),\"MyNumber (NUMBER, IT";
    assertThat(allUserItems).as(allUserItems).contains(expectedSampleHeaderLine);
    assertThat(allUserItems).as(allUserItems).contains("Complex Sample #1");
    assertThat(allUserItems).as(allUserItems).contains("Basic Sample");

    // continues with subsamples
    assertThat(allUserItems)
        .as(allUserItems)
        .contains("\n# RSpace Inventory Export\n# Exported content: SUBSAMPLES\n");
    String expectedSubSampleHeaderLine =
        "Global ID,Name,Tags,Owner,Description,Parent Sample (Global ID),"
            + "Parent Container (Global ID),Quantity,Notes,\"My extra number (NUMBER, SS";
    assertThat(allUserItems).as(allUserItems).contains(expectedSubSampleHeaderLine);
    assertThat(allUserItems).as(allUserItems).contains("Complex Sample #1.01");
    assertThat(allUserItems).as(allUserItems).contains("Basic Sample.01");

    // also includes sample templates
    assertThat(allUserItems)
        .as(allUserItems)
        .contains("\n# RSpace Inventory Export\n# Exported content: SAMPLE_TEMPLATES\n");
    assertThat(allUserItems)
        .as(allUserItems)
        .contains(",test template with mandatory text field,,");

    // also includes user's instruments (even when not stored in any container)
    assertThat(allUserItems)
        .as(allUserItems)
        .contains("\n# RSpace Inventory Export\n# Exported content: INSTRUMENTS\n");
    assertThat(allUserItems).as(allUserItems).contains(",myUserInstrument,");
    assertThat(allUserItems).as(allUserItems).contains("\n" + userInstrument.getGlobalId() + ",");

    // verify the output contains expected number of containers, samples and subsamples
    assertEquals(8, StringUtils.countMatches(allUserItems, "\nIC"), allUserItems);
    assertEquals(2, StringUtils.countMatches(allUserItems, "\nSA"), allUserItems);
    assertEquals(2, StringUtils.countMatches(allUserItems, "\nSS"), allUserItems);
    assertTrue(StringUtils.countMatches(allUserItems, "\nIT") > 0, allUserItems);
    assertEquals(1, StringUtils.countMatches(allUserItems, "\nIN"), allUserItems);
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
    assertThat(containerWithContent)
        .as(containerWithContent)
        .startsWith("# RSpace Inventory Export\n# Exported content: CONTAINERS\n");
    assertThat(containerWithContent)
        .as(containerWithContent)
        .contains(",storage shelf #1 (list container),,sampleExportUser");
    assertThat(containerWithContent)
        .as(containerWithContent)
        .contains(",box #1 (list container),,sampleExportUser");
    assertThat(containerWithContent)
        .as(containerWithContent)
        .contains(",Basic Sample.01,,sampleExportUser");
    // includes pi's subsample
    assertThat(containerWithContent)
        .as(containerWithContent)
        .contains(",mySubSample,,sampleExportPi");

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
    assertThat(allUserItems)
        .as(allUserItems)
        .startsWith("# RSpace Inventory Export\n# Exported content: CONTAINERS\n");
    assertThat(allUserItems)
        .as(allUserItems)
        .contains(",storage shelf #1 (list container),,sampleExportUser");
    assertThat(allUserItems)
        .as(allUserItems)
        .contains(",4-drawer storage unit (image container),,sampleExportUser");
    // includes pi's subsample
    assertThat(allUserItems).as(allUserItems).contains(",mySubSample,,sampleExportPi");

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
    assertThat(containerWithContentPiExport)
        .as(containerWithContentPiExport)
        .startsWith("# RSpace Inventory Export\n# Exported content: CONTAINERS\n");
    assertThat(containerWithContentPiExport)
        .as(containerWithContentPiExport)
        .contains(",piTopContainer,,sampleExportPi");
    assertThat(containerWithContentPiExport)
        .as(containerWithContentPiExport)
        .contains(",piSubContainer,,sampleExportPi");
    assertThat(containerWithContentPiExport)
        .as(containerWithContentPiExport)
        .contains(",piSubSubContainer,,sampleExportPi");
    // pi has only limited read to user container, so won't be able to export it
    assertThat(containerWithContentPiExport)
        .as(containerWithContentPiExport)
        .doesNotContain(",userContainer,,sampleExportUser");
    // pi can no longer see subsample nor subcontainer
    assertThat(containerWithContentPiExport)
        .as(containerWithContentPiExport)
        .doesNotContain(",userSubContainer,,sampleExportUser");
    assertThat(containerWithContentPiExport)
        .as(containerWithContentPiExport)
        .doesNotContain(",mySubSample,,sampleExportUser");

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
    assertThat(containerWithContentUserExport)
        .as(containerWithContentUserExport)
        .startsWith("# RSpace Inventory Export\n# Exported content: CONTAINERS\n");
    assertThat(containerWithContentUserExport)
        .as(containerWithContentUserExport)
        .contains(",piSubContainer,,sampleExportPi");
    // contains user's container, subcontainer and subsample
    assertThat(containerWithContentUserExport)
        .as(containerWithContentUserExport)
        .contains(",userContainer,,sampleExportUser");
    assertThat(containerWithContentUserExport)
        .as(containerWithContentUserExport)
        .contains(",userSubContainer,,sampleExportUser");
    assertThat(containerWithContentUserExport)
        .as(containerWithContentUserExport)
        .contains(",mySubSample,,sampleExportUser");
    // doesn't contain pi's subsubcontainer, which user can no longer see
    assertThat(containerWithContentUserExport)
        .as(containerWithContentUserExport)
        .doesNotContain(",piSubContainer2,,sampleExportPi");

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
    List<String> usernames = List.of(testUser.getUsername(), otherUser.getUsername(), "unexisting");

    ApiRuntimeException are =
        assertThrows(
            ApiRuntimeException.class,
            () ->
                exportMgr.exportUserItemsAsCsvContent(
                    usernames, CsvExportMode.FULL, true, testUser));
    assertEquals("errors.inventory.export.unexportableUsers", are.getErrorCode());
    assertEquals(otherUser.getUsername() + " and unexisting", (String) are.getArgs()[0]);

    List<GlobalIdentifier> selectedItems =
        List.of(
            new GlobalIdentifier(testUserBasicSample.getGlobalId()),
            new GlobalIdentifier(otherUserBasicSample.getGlobalId()),
            new GlobalIdentifier("IT0"));
    are =
        assertThrows(
            ApiRuntimeException.class,
            () ->
                exportMgr.exportSelectedItemsAsCsvContent(
                    selectedItems, CsvExportMode.FULL, false, false, testUser));
    assertEquals("errors.inventory.export.unexportableGlobalIds", are.getErrorCode());
    assertEquals(otherUserBasicSample.getGlobalId() + " and IT0", (String) are.getArgs()[0]);
  }

  @Test
  public void exportSelectedInstrumentToCsv() throws IOException {
    User testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("instrumentExport"));
    initialiseContentWithEmptyContent(testUser);
    ApiInstrument instrument = createBasicInstrumentForUser(testUser, "selectedInstrument");

    String instrumentExport =
        exportMgr
            .exportSelectedItemsAsCsvContent(
                List.of(new GlobalIdentifier(instrument.getGlobalId())),
                CsvExportMode.FULL,
                false,
                false,
                testUser)
            .getCombinedContent();

    assertThat(instrumentExport)
        .as(instrumentExport)
        .startsWith("# RSpace Inventory Export\n# Exported content: INSTRUMENTS\n");
    String expectedInstrumentHeader =
        "Global ID,Name,Tags,Owner,Description,"
            + "Parent Template (Global ID),Parent Template (name),Parent Container (Global ID)";
    assertThat(instrumentExport).as(instrumentExport).contains(expectedInstrumentHeader);
    assertThat(instrumentExport).as(instrumentExport).contains(",selectedInstrument,");
    assertEquals(1, StringUtils.countMatches(instrumentExport, "\nIN"), instrumentExport);
  }

  @Test
  public void exportContainerWithStoredInstrumentIncludesInstrument() throws IOException {
    User testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("instrumentExport"));
    initialiseContentWithEmptyContent(testUser);
    ApiContainer gridContainer = createBasicGridContainerForUser(testUser, 2, 2);

    ApiInstrument instrumentInContainer = new ApiInstrument();
    instrumentInContainer.setName("storedInstrument");
    ApiContainerInfo parentContainer = new ApiContainerInfo();
    parentContainer.setId(gridContainer.getId());
    instrumentInContainer.setParentContainer(parentContainer);
    instrumentInContainer.setParentLocation(new ApiContainerLocation(1, 1));
    instrumentApiMgr.createNewApiInstrument(instrumentInContainer, testUser);

    String containerWithContent =
        exportMgr
            .exportSelectedItemsAsCsvContent(
                List.of(new GlobalIdentifier(gridContainer.getGlobalId())),
                CsvExportMode.FULL,
                false,
                true,
                testUser)
            .getCombinedContent();

    // container appears first, then the stored instrument
    assertThat(containerWithContent)
        .as(containerWithContent)
        .startsWith("# RSpace Inventory Export\n# Exported content: CONTAINERS\n");
    assertThat(containerWithContent)
        .as(containerWithContent)
        .contains("\n# RSpace Inventory Export\n# Exported content: INSTRUMENTS\n");
    assertThat(containerWithContent).as(containerWithContent).contains(",storedInstrument,");
    assertEquals(1, StringUtils.countMatches(containerWithContent, "\nIN"), containerWithContent);
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
    assertThat(sampleAndSubSampleExport)
        .as(sampleAndSubSampleExport)
        .startsWith("# RSpace Inventory Export\n# Exported content: LIST_OF_MATERIALS\n");
    String expectedLomHeaderLine =
        "List of Materials (Global ID),List of Materials (Name),Used Material (Global ID),Used"
            + " Material (Name),Used Material (Type),Used Quantity\n";
    assertThat(sampleAndSubSampleExport)
        .as(sampleAndSubSampleExport)
        .contains(expectedLomHeaderLine);
    assertThat(sampleAndSubSampleExport).as(sampleAndSubSampleExport).contains(",mySample,SAMPLE");
    assertThat(sampleAndSubSampleExport)
        .as(sampleAndSubSampleExport)
        .contains(",mySubSample,SUBSAMPLE,1 ml");
  }
}
