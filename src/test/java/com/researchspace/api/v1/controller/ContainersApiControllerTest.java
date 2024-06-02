package com.researchspace.api.v1.controller;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import com.researchspace.Constants;
import com.researchspace.api.v1.model.ApiBarcode;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiContainerInfo;
import com.researchspace.api.v1.model.ApiContainerSearchResult;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiLinkItem;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.service.impl.ContentInitializerForDevRunManager;
import com.researchspace.service.impl.DocumentTagManagerImpl;
import com.researchspace.service.inventory.ContainerApiManager;
import com.researchspace.service.inventory.impl.ContainerApiManagerImpl;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;

public class ContainersApiControllerTest extends SpringTransactionalTest {

  @Autowired private ContainersApiController containersApi;

  @Autowired private ContainerApiManager containerMgr;

  @Mock private DocumentTagManagerImpl documentTagManagerMock;

  private BindingResult mockBindingResult = mock(BindingResult.class);

  @Before
  public void setUp() {
    openMocks(this);
    ReflectionTestUtils.setField(containerMgr, "documentTagManager", documentTagManagerMock);
  }

  @Test
  public void retrieveDefaultDevRunContainers() throws Exception {
    User exampleContentUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithExampleContent(exampleContentUser);
    logoutAndLoginAs(exampleContentUser);

    // retrieve top containers list
    ApiContainerSearchResult userContainers =
        containersApi.getTopContainersForUser(null, null, mockBindingResult, exampleContentUser);
    assertEquals(2, userContainers.getTotalHits().intValue());

    ApiContainerInfo listContainer = userContainers.getContainers().get(1);
    assertEquals(
        ContentInitializerForDevRunManager.EXAMPLE_TOP_LIST_CONTAINER_NAME,
        listContainer.getName());
    assertTrue(listContainer.isListContainer());
    assertEquals(3, listContainer.getContentSummary().getTotalCount());
    assertEquals(0, listContainer.getContentSummary().getSubSampleCount());
    assertEquals(3, listContainer.getContentSummary().getContainerCount());
    assertEquals(exampleContentUser.getFullName(), listContainer.getModifiedByFullName());
    assertEquals(1, listContainer.getLinks().size());
    ApiLinkItem containerLink = listContainer.getLinks().get(0);
    assertEquals(ApiLinkItem.SELF_REL, containerLink.getRel());
    assertTrue(
        containerLink.getLink().endsWith("/api/inventory/v1/containers/" + listContainer.getId()));

    // retrieve details of a container, including its content
    ApiContainer retrievedListContainer =
        containersApi.getContainerById(listContainer.getId(), true, exampleContentUser);
    assertNotNull(retrievedListContainer);
    assertEquals(
        ContentInitializerForDevRunManager.EXAMPLE_TOP_LIST_CONTAINER_NAME,
        retrievedListContainer.getName());
    assertNull(retrievedListContainer.getParentContainer());
    assertEquals(3, retrievedListContainer.getLocations().size());
    ApiContainerInfo subcontainer =
        (ApiContainerInfo) retrievedListContainer.getLocations().get(0).getContent();
    assertEquals("box #1 (list container)", subcontainer.getName());
    assertEquals(4, subcontainer.getContentSummary().getTotalCount());
    assertEquals(1, subcontainer.getContentSummary().getSubSampleCount());
    assertEquals(3, subcontainer.getContentSummary().getContainerCount());
    assertEquals(exampleContentUser.getFullName(), subcontainer.getModifiedByFullName());

    // no locations if includeContent flag false
    ApiContainer retrievedContainerNoContent =
        containersApi.getContainerById(listContainer.getId(), false, exampleContentUser);
    assertNull(retrievedContainerNoContent.getLocations());

    // retrieve details of a subcontainer
    ApiContainer retrievedSubContainer =
        containersApi.getContainerById(subcontainer.getId(), true, exampleContentUser);
    assertEquals("box #1 (list container)", retrievedSubContainer.getName());
    assertEquals(
        ContentInitializerForDevRunManager.EXAMPLE_TOP_LIST_CONTAINER_NAME,
        retrievedSubContainer.getParentContainer().getName());
    assertEquals(4, retrievedSubContainer.getLocations().size());

    // retrieve image container, check thumbnail and attachment
    ApiContainerInfo imageContainer = userContainers.getContainers().get(0);
    assertEquals(
        ContentInitializerForDevRunManager.EXAMPLE_TOP_IMAGE_CONTAINER_NAME,
        imageContainer.getName());
    assertTrue(imageContainer.isImageContainer());
    assertEquals(1, imageContainer.getAttachments().size());
    assertEquals(
        ContentInitializerForDevRunManager.EXAMPLE_TOP_IMAGE_CONTAINER_ATTACHMENT_NAME,
        imageContainer.getAttachments().get(0).getName());
    List<ApiLinkItem> imageContainerLinks = imageContainer.getLinks();
    assertEquals(4, imageContainerLinks.size()); // self/image/thumbnail/locations links

    ResponseEntity<byte[]> bytes =
        containersApi.getContainerThumbnail(imageContainer.getId(), exampleContentUser);
    assertEquals(3177, bytes.getBody().length);
    assertEquals("image/jpeg", bytes.getHeaders().getContentType().toString());

    // retrieve default locations, should be defined but empty
    ApiContainer retrievedImageContainer =
        containersApi.getContainerById(imageContainer.getId(), true, exampleContentUser);
    assertEquals(4, retrievedImageContainer.getLocations().size());
    assertEquals(227, retrievedImageContainer.getLocations().get(0).getCoordX());
    assertEquals(114, retrievedImageContainer.getLocations().get(0).getCoordY());
    assertNull(retrievedImageContainer.getLocations().get(0).getContent());
  }

  @Test
  public void createContainerShouldUpdateTags() throws Exception {
    User testUser = createInitAndLoginAnyUser();
    when(mockBindingResult.hasErrors()).thenReturn(false);
    ApiContainer defaultContainer = new ApiContainer();
    containersApi.createNewContainer(defaultContainer, mockBindingResult, testUser);
    verify(documentTagManagerMock, never()).updateUserOntologyDocument(eq(testUser));
    defaultContainer = new ApiContainer();
    // set tags - create will write to ontology doc
    defaultContainer.setApiTagInfo("Some tags");
    containersApi.createNewContainer(defaultContainer, mockBindingResult, testUser);
    verify(documentTagManagerMock, times(1)).updateUserOntologyDocument(eq(testUser));
  }

  @Test
  public void restoreContainerShouldUpdateTags() throws Exception {
    User testUser = createInitAndLoginAnyUser();
    when(mockBindingResult.hasErrors()).thenReturn(false);
    ApiContainer defaultContainer = new ApiContainer();
    ApiContainer createdContainer =
        containersApi.createNewContainer(defaultContainer, mockBindingResult, testUser);
    containersApi.deleteContainer(createdContainer.getId(), testUser);
    verify(documentTagManagerMock, never()).updateUserOntologyDocument(eq(testUser));
    containersApi.restoreDeletedContainer(createdContainer.getId(), testUser);
    verify(documentTagManagerMock, never()).updateUserOntologyDocument(eq(testUser));
    createdContainer =
        containersApi.createNewContainer(defaultContainer, mockBindingResult, testUser);
    // set tags - restore will write to ontology doc
    createdContainer.setApiTagInfo("Some tags");
    containersApi.updateContainer(
        createdContainer.getId(), createdContainer, mockBindingResult, testUser);
    verify(documentTagManagerMock, times(1)).updateUserOntologyDocument(eq(testUser));
    containersApi.deleteContainer(createdContainer.getId(), testUser);
    verify(documentTagManagerMock, times(2)).updateUserOntologyDocument(eq(testUser));
    containersApi.restoreDeletedContainer(createdContainer.getId(), testUser);
    verify(documentTagManagerMock, times(3)).updateUserOntologyDocument(eq(testUser));
  }

  @Test
  public void updateContainerShouldUpdateTags() throws Exception {
    User testUser = createInitAndLoginAnyUser();
    when(mockBindingResult.hasErrors()).thenReturn(false);
    ApiContainer defaultContainer = new ApiContainer();
    ApiContainer createdContainer =
        containersApi.createNewContainer(defaultContainer, mockBindingResult, testUser);
    // change name - no writing to use ontology doc
    ApiContainer containerUpdate = new ApiContainer();
    containerUpdate.setName("updated name");
    ApiContainer retrievedContainer =
        containersApi.getContainerById(createdContainer.getId(), true, testUser);
    containersApi.updateContainer(
        retrievedContainer.getId(), containerUpdate, mockBindingResult, testUser);
    verify(documentTagManagerMock, never()).updateUserOntologyDocument(eq(testUser));
    retrievedContainer = containersApi.getContainerById(createdContainer.getId(), true, testUser);
    // change tags - write to ontology doc
    retrievedContainer.setApiTagInfo("some tags");
    ApiContainer updated =
        containersApi.updateContainer(
            retrievedContainer.getId(), retrievedContainer, mockBindingResult, testUser);
    verify(documentTagManagerMock, times(1)).updateUserOntologyDocument(eq(testUser));
    assertEquals("some tags", updated.getDBStringFromTags());
  }

  @Test
  public void allTagsCanBeDeletedFromAnItem() throws Exception {
    User testUser = createInitAndLoginAnyUser();
    when(mockBindingResult.hasErrors()).thenReturn(false);
    ApiContainer defaultContainer = new ApiContainer();
    ApiContainer createdContainer =
        containersApi.createNewContainer(defaultContainer, mockBindingResult, testUser);
    ApiContainer retrievedContainer =
        containersApi.getContainerById(createdContainer.getId(), true, testUser);
    retrievedContainer.setApiTagInfo("some tags");
    ApiContainer updated =
        containersApi.updateContainer(
            retrievedContainer.getId(), retrievedContainer, mockBindingResult, testUser);
    assertEquals("some tags", updated.getDBStringFromTags());
    retrievedContainer.setApiTagInfo("");
    updated =
        containersApi.updateContainer(
            retrievedContainer.getId(), retrievedContainer, mockBindingResult, testUser);
    assertEquals("", updated.getDBStringFromTags());
    retrievedContainer.setApiTagInfo(null);
    updated =
        containersApi.updateContainer(
            retrievedContainer.getId(), retrievedContainer, mockBindingResult, testUser);
    assertEquals("", updated.getDBStringFromTags());
  }

  @Test
  public void deleteContainerShouldUpdateTags() throws Exception {
    User testUser = createInitAndLoginAnyUser();
    when(mockBindingResult.hasErrors()).thenReturn(false);

    ApiContainer defaultContainer = new ApiContainer();
    ApiContainer createdContainer =
        containersApi.createNewContainer(defaultContainer, mockBindingResult, testUser);
    containersApi.deleteContainer(createdContainer.getId(), testUser);
    verify(documentTagManagerMock, never()).updateUserOntologyDocument(eq(testUser));
    createdContainer =
        containersApi.createNewContainer(defaultContainer, mockBindingResult, testUser);
    // set tags - delete will write to ontology doc
    createdContainer.setApiTagInfo("Some tags");
    containersApi.updateContainer(
        createdContainer.getId(), createdContainer, mockBindingResult, testUser);
    verify(documentTagManagerMock, times(1)).updateUserOntologyDocument(eq(testUser));
    containersApi.deleteContainer(createdContainer.getId(), testUser);
    verify(documentTagManagerMock, times(2)).updateUserOntologyDocument(eq(testUser));
  }

  @Test
  public void changeContainerOwnerShouldUpdateTags() throws Exception {
    User testUser = createInitAndLoginAnyUser();
    User piUser = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    initialiseContentWithEmptyContent(piUser);
    Group group = createGroup("group", piUser);
    addUsersToGroup(piUser, group, testUser);
    when(mockBindingResult.hasErrors()).thenReturn(false);
    ApiContainer defaultContainer = new ApiContainer();
    defaultContainer =
        containersApi.createNewContainer(defaultContainer, mockBindingResult, testUser);
    containersApi.changeContainerOwner(
        defaultContainer.getId(), defaultContainer, mockBindingResult, piUser);
    verify(documentTagManagerMock, never()).updateUserOntologyDocument(eq(testUser));
    verify(documentTagManagerMock, never()).updateUserOntologyDocument(eq(piUser));
    // set tags - change owner will write to ontology doc
    defaultContainer.setApiTagInfo("Some tags");
    containersApi.updateContainer(
        defaultContainer.getId(), defaultContainer, mockBindingResult, piUser);
    verify(documentTagManagerMock, times(1)).updateUserOntologyDocument(eq(piUser));
    containersApi.changeContainerOwner(
        defaultContainer.getId(), defaultContainer, mockBindingResult, testUser);
    verify(documentTagManagerMock, times(1)).updateUserOntologyDocument(eq(testUser));
  }

  @Test
  public void createRetrieveUpdateNewContainer() throws Exception {

    User testUser = createInitAndLoginAnyUser();
    assertTrue(testUser.isContentInitialized());
    when(mockBindingResult.hasErrors()).thenReturn(false);

    ApiContainer defaultContainer = new ApiContainer();
    ApiContainer createdContainer =
        containersApi.createNewContainer(defaultContainer, mockBindingResult, testUser);
    assertNotNull(createdContainer.getId());

    ApiContainer retrievedContainer =
        containersApi.getContainerById(createdContainer.getId(), true, testUser);
    assertEquals(ContainerApiManagerImpl.CONTAINER_DEFAULT_NAME, retrievedContainer.getName());
    assertEquals(0, retrievedContainer.getExtraFields().size());
    assertEquals(0, retrievedContainer.getBarcodes().size());

    // change name, add new extra field
    ApiContainer containerUpdate = new ApiContainer();
    containerUpdate.setName("updated name");
    ApiExtraField extraFieldAddition = new ApiExtraField();
    extraFieldAddition.setNewFieldRequest(true);
    extraFieldAddition.setContent("new field content");
    containerUpdate.getExtraFields().add(extraFieldAddition);

    ApiContainer updatedContainer =
        containersApi.updateContainer(
            retrievedContainer.getId(), containerUpdate, mockBindingResult, testUser);
    assertEquals(1, updatedContainer.getLinks().size());
    retrievedContainer = containersApi.getContainerById(createdContainer.getId(), true, testUser);
    assertEquals(updatedContainer, retrievedContainer);
    assertEquals("updated name", retrievedContainer.getName());
    assertEquals(1, updatedContainer.getExtraFields().size());
    assertEquals(0, updatedContainer.getBarcodes().size());
    assertEquals(0, retrievedContainer.getContentSummary().getTotalCount());

    ApiExtraField newlyAddedExtraField = retrievedContainer.getExtraFields().get(0);
    assertNotNull(newlyAddedExtraField.getId());
    assertEquals("new field content", newlyAddedExtraField.getContent());

    // create changeset that modifies newly added extra field
    ApiExtraField extraFieldModification = new ApiExtraField();
    extraFieldModification.setId(newlyAddedExtraField.getId());
    extraFieldModification.setContent("updated field content");
    containerUpdate.getExtraFields().add(extraFieldModification);
    containerUpdate = new ApiContainer();
    containerUpdate.getExtraFields().add(extraFieldModification);

    // apply changeset
    updatedContainer =
        containersApi.updateContainer(
            retrievedContainer.getId(), containerUpdate, mockBindingResult, testUser);
    retrievedContainer = containersApi.getContainerById(createdContainer.getId(), true, testUser);
    assertEquals(updatedContainer, retrievedContainer);
    assertEquals(1, retrievedContainer.getExtraFields().size());
    assertEquals("updated field content", retrievedContainer.getExtraFields().get(0).getContent());

    // add content to the container: a subsample and another container
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples();
    newSample.setName("mySample");
    newSample.setNewBase64Image(getBase64Image());
    ApiSubSample subSample = new ApiSubSample();
    subSample.setName("mySubSample");
    subSample.setParentContainer(retrievedContainer);
    newSample.setSubSamples(Arrays.asList(subSample));
    ApiSampleWithFullSubSamples createdSample =
        sampleApiMgr.createNewApiSample(newSample, testUser);
    ApiSubSample createdSubSample = createdSample.getSubSamples().get(0);

    ApiContainer subContainer = new ApiContainer();
    subContainer.setNewBase64Image(getBase64Image());
    subContainer.setParentContainer(retrievedContainer);
    ApiContainer createdSubContainer =
        containersApi.createNewContainer(subContainer, mockBindingResult, testUser);
    assertEquals(3, createdSubContainer.getLinks().size()); // self + images

    // reload details of the container, check expected details of the content
    retrievedContainer = containersApi.getContainerById(createdContainer.getId(), true, testUser);
    assertEquals(2, retrievedContainer.getContentSummary().getTotalCount());
    // check subsample
    ApiInventoryRecordInfo contentInFirstLocation =
        retrievedContainer.getLocations().get(0).getContent();
    assertEquals(createdSubSample.getGlobalId(), contentInFirstLocation.getGlobalId());
    assertEquals(3, contentInFirstLocation.getLinks().size());
    // verify image links are pointing to image of parent sample
    assertEquals(
        2,
        contentInFirstLocation.getLinks().stream()
            .filter(ali -> ali.getLink().contains("v1/samples"))
            .count());
    // check subcontainer
    ApiInventoryRecordInfo contentInSecondLocation =
        retrievedContainer.getLocations().get(1).getContent();
    assertEquals(createdSubContainer.getGlobalId(), contentInSecondLocation.getGlobalId());
    assertEquals(3, contentInSecondLocation.getLinks().size());
  }

  @Test
  public void createUpdateDeleteContainerBarcodes() throws BindException {

    User testUser = createInitAndLoginAnyUser();
    assertTrue(testUser.isContentInitialized());
    when(mockBindingResult.hasErrors()).thenReturn(false);

    // container with a barcode
    ApiContainer defaultContainer = new ApiContainer();
    ApiBarcode barcodeRequest = new ApiBarcode();
    barcodeRequest.setData("123-defaultBarcode");
    barcodeRequest.setDescription("testDesc");
    barcodeRequest.setFormat("qr");
    barcodeRequest.setNewBarcodeRequest(true);
    defaultContainer.setBarcodes(List.of(barcodeRequest));

    ApiContainer createdContainer =
        containersApi.createNewContainer(defaultContainer, mockBindingResult, testUser);
    assertNotNull(createdContainer.getId());

    ApiContainer retrievedContainer =
        containersApi.getContainerById(createdContainer.getId(), true, testUser);
    assertEquals(ContainerApiManagerImpl.CONTAINER_DEFAULT_NAME, retrievedContainer.getName());
    assertEquals(1, retrievedContainer.getBarcodes().size());

    // assert a few barcode details
    ApiBarcode firstBarcode = retrievedContainer.getBarcodes().get(0);
    assertEquals(barcodeRequest.getData(), firstBarcode.getData());
    assertEquals(barcodeRequest.getDescription(), firstBarcode.getDescription());
    assertEquals(barcodeRequest.getFormat(), firstBarcode.getFormat());
    assertEquals(1, firstBarcode.getLinks().size());
    assertEquals(
        "http://localhost:8080/api/inventory/v1/barcodes%3Fcontent=123-defaultBarcode&barcodeType=QR",
        firstBarcode.getLinkOfType(ApiLinkItem.ENCLOSURE_REL).get().getLink());

    // change container name, update barcode description, add another barcode
    ApiContainer containerUpdate = new ApiContainer();
    containerUpdate.setName("updated name");
    ApiBarcode barcodeUpdateRequest = new ApiBarcode();
    barcodeUpdateRequest.setId(firstBarcode.getId());
    barcodeUpdateRequest.setDescription("updatedDesc");
    ApiBarcode secondBarcodeRequest = new ApiBarcode();
    secondBarcodeRequest.setData("123-anotherBarcode");
    secondBarcodeRequest.setDescription("anotherDesc");
    secondBarcodeRequest.setFormat("qr2");
    secondBarcodeRequest.setNewBarcodeRequest(true);
    containerUpdate.setBarcodes(List.of(barcodeUpdateRequest, secondBarcodeRequest));

    ApiContainer updatedContainer =
        containersApi.updateContainer(
            retrievedContainer.getId(), containerUpdate, mockBindingResult, testUser);
    assertEquals(1, updatedContainer.getLinks().size());
    retrievedContainer = containersApi.getContainerById(createdContainer.getId(), true, testUser);
    assertEquals(updatedContainer, retrievedContainer);
    assertEquals("updated name", retrievedContainer.getName());
    assertEquals(2, updatedContainer.getBarcodes().size());

    // check 1st barcode updated
    firstBarcode = updatedContainer.getBarcodes().get(0);
    assertEquals(barcodeUpdateRequest.getDescription(), firstBarcode.getDescription());
    // check 2nd barcode added
    ApiBarcode secondBarcode = updatedContainer.getBarcodes().get(1);
    assertEquals(secondBarcodeRequest.getData(), secondBarcode.getData());
    assertEquals(secondBarcodeRequest.getDescription(), secondBarcode.getDescription());
    assertEquals(secondBarcodeRequest.getFormat(), secondBarcode.getFormat());

    // delete initial barcode
    containerUpdate = new ApiContainer();
    ApiBarcode barcodeDeletionUpdate = new ApiBarcode();
    barcodeDeletionUpdate.setId(firstBarcode.getId());
    barcodeDeletionUpdate.setDeleteBarcodeRequest(true);
    containerUpdate.setBarcodes(List.of(barcodeDeletionUpdate));

    // check latest container
    updatedContainer =
        containersApi.updateContainer(
            retrievedContainer.getId(), containerUpdate, mockBindingResult, testUser);
    assertEquals(1, updatedContainer.getLinks().size());
    retrievedContainer = containersApi.getContainerById(createdContainer.getId(), true, testUser);
    assertEquals(updatedContainer, retrievedContainer);
    assertEquals(1, updatedContainer.getBarcodes().size());
  }

  @Test
  public void saveImageContainer() throws BindException, IOException {

    User testUser = createInitAndLoginAnyUser();

    // create image container that doesn't have an image yet
    ApiContainer imageContainerWithoutImage =
        new ApiContainer("imageContainerNoImg", ContainerType.IMAGE);
    ApiContainer createdContainer =
        containersApi.createNewContainer(imageContainerWithoutImage, mockBindingResult, testUser);
    assertTrue(createdContainer.isImageContainer());
    assertEquals(1, createdContainer.getLinks().size()); // only self link

    // create container with png base64 image (for main image and locations)
    ApiContainer imageContainer = new ApiContainer("imageContainer", ContainerType.IMAGE);
    String base64Png =
        "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAQIAAAESAQMAAAAsV0mIAAAAAXNSR0IArs4c6QAAAARn"
            + "QU1BAACxjwv8YQUAAAAGUExURf///wAAAFXC034AAAAJcEhZcwAADsMAAA7DAcdvqGQAAABWSURBVGje7dUhDsAgEETR5VYcv8f"
            + "CgUEg1rXdhOR9/ZKRE5LO2kwbH4snHe8EQRAEQWxR88g+myAIgiDeCo9MEARBEP+Lmr/1yARBEARxhyj5fSktYgFPS1k85TqeJQ"
            + "AAAABJRU5ErkJggg==";
    imageContainer.setNewBase64Image(base64Png);
    imageContainer.setNewBase64LocationsImage(base64Png);

    // check saved image files
    createdContainer =
        containersApi.createNewContainer(imageContainer, mockBindingResult, testUser);
    assertTrue(createdContainer.isImageContainer());
    assertEquals(4, createdContainer.getLinks().size()); // self/image/thumbnail/locationsImg link
    Long containerId = createdContainer.getId();
    assertNotNull(containerId);
    Container container = containerMgr.getContainerById(containerId, testUser);
    assertEquals(
        "container-imageCo_" + container.getGlobalIdentifier() + ".png",
        container.getImageFileProperty().getFileName());
    assertEquals("211", container.getImageFileProperty().getFileSize());
    assertEquals(
        "container-imageCo_" + container.getGlobalIdentifier() + "_thumbnail.png",
        container.getThumbnailFileProperty().getFileName());
    int thumbnailSize = Integer.valueOf(container.getThumbnailFileProperty().getFileSize());
    assertTrue(
        thumbnailSize == 117 || thumbnailSize == 129,
        "unexpected size: " + thumbnailSize); // different results depending on jdk version
    assertEquals(
        "container_" + container.getGlobalIdentifier() + "_locations.png",
        container.getLocationsImageFileProperty().getFileName());
    assertEquals("211", container.getLocationsImageFileProperty().getFileSize());

    // check png thumbnail retrieval
    ResponseEntity<byte[]> bytes = containersApi.getContainerThumbnail(containerId, testUser);
    int bodyLength = bytes.getBody().length;
    assertTrue(
        bodyLength == 117 || bodyLength == 129,
        "unexpected body size: " + bodyLength); // different results depending on jdk version
    assertEquals("image/x-png", bytes.getHeaders().getContentType().toString());

    // update with jpeg file
    String base64Jpeg =
        "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEAXwBfAAD/4QBoRXhpZgAATU0AKgAAAAgABAEaAAUAAAABAA"
            + "AAPgEbAAUAAAABAAAARgEoAAMAAAABAAIAAAExAAIAAAARAAAATgAAAAAAAABfAAAAAQAAAF8AAAABcGFpbnQubmV0IDQuMi4xM"
            + "wAA/9sAQwCgbniMeGSgjIKMtKqgvvD///Dc3PD//////////////////////////////////////////////////////////9sA"
            + "QwGqtLTw0vD//////////////////////////////////////////////////////////////////////////////8AAEQgAggC"
            + "MAwEiAAIRAQMRAf/EAB8AAAEFAQEBAQEBAAAAAAAAAAABAgMEBQYHCAkKC//EALUQAAIBAwMCBAMFBQQEAAABfQECAwAEEQUSIT"
            + "FBBhNRYQcicRQygZGhCCNCscEVUtHwJDNicoIJChYXGBkaJSYnKCkqNDU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0d"
            + "XZ3eHl6g4SFhoeIiYqSk5SVlpeYmZqio6Slpqeoqaqys7S1tre4ubrCw8TFxsfIycrS09TV1tfY2drh4uPk5ebn6Onq8fLz9PX2"
            + "9/j5+v/EAB8BAAMBAQEBAQEBAQEAAAAAAAABAgMEBQYHCAkKC//EALURAAIBAgQEAwQHBQQEAAECdwABAgMRBAUhMQYSQVEHYXE"
            + "TIjKBCBRCkaGxwQkjM1LwFWJy0QoWJDThJfEXGBkaJicoKSo1Njc4OTpDREVGR0hJSlNUVVZXWFlaY2RlZmdoaWpzdHV2d3h5eo"
            + "KDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uLj5OXm5+jp6vLz9PX29/j5+v/aA"
            + "AwDAQACEQMRAD8AmooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooo"
            + "oAKKKKACiiigAooooAKKKKAEY7Rk03zR6Glk+4aSNQUGQKClaweaPQ0eaPQ07avoKNq+goDTsN80eho80ehp21fQUbV9BQGnYb5"
            + "o9DR5o9DTtq+go2r6CgNOwgkBOMGnVEOJuP8APFS0CYUUUUCCiiigAooooAKKKKAGyfcNEf3BRJ9w0R/cFA+nzHUUUUCCiiigAo"
            + "oooAj/AOW3+fSpKj/5bf59KkoG+noFFFFAgooooAKKKKACiiigBsn3DRH9wUSfcNEf3BQPp8x1FFFAgooooAKKKKAI/wDlt/n0q"
            + "So/+W3+fSpKBvp6BRRRQIKKKKACiiigAooooAbJ9w0kbAIMkU5huGDTfKHqaClaw7cvqKNy+opvlD1NHlD1NAadx25fUUbl9RTf"
            + "KHqaPKHqaA07jty+oo3L6im+UPU0eUPU0Bp3Gjmbj/PFS00RgHOTTqBMKKKKBBRRRQAUUUUAFFFFABRRRQAUUUUAFFFFABRRRQA"
            + "UUUUAFFFFABRRRQAUUUUAFFFFABRRRQAUUUUAFFFFABRRRQAUUUUAFFFFABRRRQAUUUUAFFFFABRRRQAUUUUAFFFFABRRRQAUUU"
            + "UAFFFFABRRRQB//9k=";
    imageContainer.setNewBase64Image(base64Jpeg);
    containersApi.updateContainer(containerId, imageContainer, mockBindingResult, testUser);
    container = containerMgr.getContainerById(containerId, testUser);
    assertEquals(
        "container-imageCo_" + container.getGlobalIdentifier() + ".jpg",
        container.getImageFileProperty().getFileName());
    assertEquals("1250", container.getImageFileProperty().getFileSize());
    assertEquals(
        "container-imageCo_" + container.getGlobalIdentifier() + "_thumbnail.jpg",
        container.getThumbnailFileProperty().getFileName());
    assertEquals("1682", container.getThumbnailFileProperty().getFileSize());

    // check jpg thumbnail retrieval
    bytes = containersApi.getContainerThumbnail(containerId, testUser);
    assertEquals(1682, bytes.getBody().length);
    assertEquals("image/jpeg", bytes.getHeaders().getContentType().toString());
  }

  @Test
  public void moveContainerIntoAnotherContainer() throws Exception {

    User testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(testUser);

    ApiContainer listContainer = createBasicContainerForUser(testUser);
    ApiContainer imageContainer = createBasicImageContainerForUser(testUser);
    ApiContainer workbench = getWorkbenchForUser(testUser);

    // subcontainer to move around
    ApiContainer movingBox = createBasicContainerForUser(testUser, "moving subcontainer");
    assertEquals(workbench.getId(), movingBox.getParentContainer().getId());
    assertEquals(1, movingBox.getParentContainers().size());

    workbench = getWorkbenchForUser(testUser);
    int initialWorkbenchCount = workbench.getContentSummary().getTotalCount();
    assertEquals(3, initialWorkbenchCount);

    // move to list container
    ApiContainer updateRequest = new ApiContainer();
    updateRequest.setParentContainer(listContainer);
    ApiContainer updatedMovingBox =
        containersApi.updateContainer(
            movingBox.getId(), updateRequest, mockBindingResult, testUser);
    assertEquals(listContainer.getId(), updatedMovingBox.getParentContainer().getId());
    assertEquals(2, updatedMovingBox.getParentContainers().size());

    // verify target container updated
    workbench = getWorkbenchForUser(testUser);
    assertEquals(initialWorkbenchCount - 1, workbench.getContentSummary().getTotalCount());
    listContainer = containersApi.getContainerById(listContainer.getId(), true, testUser);
    assertEquals(1, listContainer.getContentSummary().getTotalCount());

    // move to image container
    updateRequest.setParentContainer(imageContainer);
    updateRequest.setParentLocation(imageContainer.getLocations().get(0));
    updatedMovingBox =
        containersApi.updateContainer(
            movingBox.getId(), updateRequest, mockBindingResult, testUser);
    assertEquals(imageContainer.getId(), updatedMovingBox.getParentContainer().getId());
    assertEquals(2, updatedMovingBox.getParentContainers().size());

    // verify source and target containers updated
    listContainer = containersApi.getContainerById(listContainer.getId(), true, testUser);
    assertEquals(0, listContainer.getContentSummary().getTotalCount());
    imageContainer = containersApi.getContainerById(imageContainer.getId(), true, testUser);
    assertEquals(1, imageContainer.getContentSummary().getTotalCount());

    // move back to list container
    updateRequest.setParentContainer(listContainer);
    updateRequest.setParentLocation(null);
    updatedMovingBox =
        containersApi.updateContainer(
            movingBox.getId(), updateRequest, mockBindingResult, testUser);
    assertEquals(listContainer.getId(), updatedMovingBox.getParentContainer().getId());
    assertEquals(2, updatedMovingBox.getParentContainers().size());

    // verify source and target containers updated
    listContainer = containersApi.getContainerById(listContainer.getId(), true, testUser);
    assertEquals(1, listContainer.getContentSummary().getTotalCount());
    imageContainer = containersApi.getContainerById(imageContainer.getId(), true, testUser);
    assertEquals(0, imageContainer.getContentSummary().getTotalCount());
  }

  @Test
  public void workbenchRequestsBlockedByContainersController() throws BindException {
    User user = createInitAndLoginAnyUser();
    ApiContainer workbench = getWorkbenchForUser(user);

    // cannot update workbench
    ApiContainer updateRequest = new ApiContainer();
    updateRequest.setName("my name");
    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                containersApi.updateContainer(
                    workbench.getId(), updateRequest, mockBindingResult, user));
    assertEquals("Container with id " + workbench.getId() + " is a workbench", iae.getMessage());
  }
}
