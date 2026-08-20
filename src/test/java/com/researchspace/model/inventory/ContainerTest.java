package com.researchspace.model.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.core.testutil.ModelTestUtils;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.FileProperty;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.model.record.TestFactory;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ContainerTest {

  User anyUser;

  @BeforeEach
  void setup() {
    anyUser = TestFactory.createAnyUser("any");
  }

  @Test
  void testInitialProperties() {
    Container container = new Container(ContainerType.LIST);

    assertNotNull(container.getModificationDate());
    assertNotNull(container.getCreationDate());
    assertFalse(container.isDeleted());
  }

  @Test
  void addContentToListContainer() throws Exception {

    Container listContainer = Container.createListContainer(true, true, true);
    listContainer.setId(1L);

    // try creating locations explicitly - only allowed for image container
    IllegalArgumentException iae = Assertions.assertThrows(IllegalArgumentException.class,
        () -> listContainer.createNewImageContainerLocation(2, 2));
    assertEquals("LIST container cannot add locations directly", iae.getMessage());
    assertEquals(0, listContainer.getLocations().size());
    assertEquals(0, listContainer.getLocationsCount());

    // try adding subsample to unspecified location for each container - only allowed for list container
    SubSample subSample = new SubSample();
    listContainer.addToNewLocation(new SubSample());
    assertEquals(1, listContainer.getContentCount());
    assertEquals(1, listContainer.getContentCountSubSamples());
    assertEquals(0, listContainer.getContentCountContainers());
    assertEquals(1, listContainer.getLocations().size());
    assertEquals(0, listContainer.getLocationsCount());

    // try adding subsample to location with coordinates for each container - allowed for grid/image containers
    iae = Assertions.assertThrows(IllegalArgumentException.class,
        () -> listContainer.addToNewLocationWithCoords(subSample, 2, 2));
    assertEquals("LIST container can't use explicit location coordinates", iae.getMessage());
    assertEquals(1, listContainer.getContentCount()); // previously added
    assertEquals(1, listContainer.getLocations().size());
    assertEquals(0, listContainer.getLocationsCount());
  }

  @Test
  void addContentToGridContainer() throws Exception {

    Container gridContainer6by4 = Container.createGridContainer(6, 4, true, true, true);
    gridContainer6by4.setId(2L);
    assertTrue(gridContainer6by4.isCanStoreContainers());

    // try creating locations explicitly - only allowed for image container
    IllegalArgumentException iae = Assertions.assertThrows(IllegalArgumentException.class,
        () -> gridContainer6by4.createNewImageContainerLocation(2, 2));
    assertEquals("GRID container cannot add locations directly", iae.getMessage());
    assertEquals(0, gridContainer6by4.getLocations().size());
    assertEquals(24, gridContainer6by4.getLocationsCount());

    // try adding subsample to unspecified location for each container - only allowed for list container
    SubSample subSample = new SubSample();
    iae = Assertions.assertThrows(IllegalArgumentException.class,
        () -> gridContainer6by4.addToNewLocation(subSample));
    assertEquals("GRID container cannot store content without providing specific coordinates",
        iae.getMessage());
    assertEquals(0, gridContainer6by4.getContentCount());
    assertEquals(0, gridContainer6by4.getLocations().size());

    // for grid container: try adding subsample to location with wrong coordinates
    iae = Assertions.assertThrows(IllegalArgumentException.class,
        () -> gridContainer6by4.addToNewLocationWithCoords(subSample, 7, 2));
    assertEquals(
        "Requested new location (7,2) is outside grid container dimensions (columns:6, rows:4)",
        iae.getMessage());
    assertEquals(0, gridContainer6by4.getContentCount());
    assertEquals(0, gridContainer6by4.getLocations().size());

    iae = Assertions.assertThrows(IllegalArgumentException.class,
        () -> gridContainer6by4.addToNewLocationWithCoords(subSample, 2, 5));
    assertEquals(
        "Requested new location (2,5) is outside grid container dimensions (columns:6, rows:4)",
        iae.getMessage());
    assertEquals(0, gridContainer6by4.getContentCount());
    assertEquals(0, gridContainer6by4.getLocations().size());

    // try adding subsample to location with coordinates for each container - allowed for grid/image containers
    gridContainer6by4.addToNewLocationWithCoords(subSample, 2, 2);
    assertEquals(1, gridContainer6by4.getContentCount());
    assertEquals(1, gridContainer6by4.getContentCountSubSamples());
    assertEquals(0, gridContainer6by4.getContentCountContainers());
    assertEquals(1, gridContainer6by4.getLocations().size());
  }

  @Test
  void addContentToImageContainer() throws Exception {

    Container imageContainer = Container.createImageContainer(true, true, true);
    imageContainer.setLocationsImageFileProperty(new FileProperty());
    imageContainer.setId(3L);

    imageContainer.createNewImageContainerLocation(2, 2);
    assertEquals(1, imageContainer.getLocations().size());
    assertEquals(1, imageContainer.getLocationsCount());

    // try adding subsample to unspecified location for each container - only allowed for list container
    SubSample subSample = new SubSample();
    IllegalArgumentException iae = Assertions.assertThrows(IllegalArgumentException.class,
        () -> imageContainer.addToNewLocation(subSample));
    assertEquals("IMAGE container cannot store content without providing specific coordinates",
        iae.getMessage());
    assertEquals(0, imageContainer.getContentCount());
    assertEquals(1, imageContainer.getLocations().size());

    // try adding subsample to location with coordinates for each container - allowed for grid/image containers
    iae = Assertions.assertThrows(IllegalArgumentException.class,
        () -> imageContainer.addToNewLocationWithCoords(subSample, 2, 2));
    assertEquals("Image container must provide target location id, not coordinates",
        iae.getMessage());
    assertEquals(0, imageContainer.getContentCount());
    assertEquals(1, imageContainer.getLocations().size());
    // image container can add using location though
    imageContainer.setRecordInLocation(subSample, imageContainer.getLocations().get(0));
    assertEquals(1, imageContainer.getContentCount());
    assertEquals(1, imageContainer.getLocations().size());
  }

  @Test
  void moveContentBetweenContainerTypes() throws Exception {

    Container listContainer1 = Container.createListContainer(true, true, true);
    listContainer1.setId(1L);

    // create a subsample and a container to move around
    SubSample subSample = new SubSample();
    assertNull(subSample.getParentId());
    Container movingContainer = Container.createListContainer(true, true, true);
    movingContainer.setId(5L);
    assertNull(movingContainer.getParentId());

    // to list container
    subSample.moveToNewParent(listContainer1);
    assertEquals(listContainer1.getId(), subSample.getParentId());
    movingContainer.moveToNewParent(listContainer1);
    assertEquals(listContainer1.getId(), movingContainer.getParentId());
    assertEquals(2, listContainer1.getContentCount());
    assertEquals(1, listContainer1.getContentCountSubSamples());
    assertEquals(1, listContainer1.getContentCountContainers());
    assertEquals(2, listContainer1.getLocations().size());

    // to another list container
    Container listContainer2 = Container.createListContainer(true, true, true);
    listContainer2.setId(2L);

    subSample.moveToNewParent(listContainer2);
    assertEquals(listContainer2.getId(), subSample.getParentId());
    movingContainer.moveToNewParent(listContainer2);
    assertEquals(listContainer2.getId(), movingContainer.getParentId());
    assertEquals(0, listContainer1.getContentCount());
    assertEquals(0, listContainer1.getLocations().size());
    assertEquals(2, listContainer2.getContentCount());
    assertEquals(2, listContainer2.getLocations().size());

    // to grid container
    Container gridContainer6by4 = Container.createGridContainer(6, 4, false, true, false);
    gridContainer6by4.configureAsGridLayoutContainer(6, 4);
    gridContainer6by4.setId(3L);

    subSample.moveToNewParentWithCoords(gridContainer6by4, 2, 2);
    assertEquals(gridContainer6by4.getId(), subSample.getParentId());
    IllegalArgumentException iae = Assertions.assertThrows(IllegalArgumentException.class,
        () -> movingContainer.moveToNewParentWithCoords(gridContainer6by4, 2, 3));
    assertEquals("Container IC3 can't hold record of type: CONTAINER", iae.getMessage());
    assertEquals(0, listContainer2.getContentCount());
    assertEquals(0, listContainer2.getLocations().size());
    assertEquals(1, gridContainer6by4.getContentCount());
    assertEquals(2, gridContainer6by4.getLocations().size());

    // to image container
    Container imageContainer = Container.createImageContainer(true, true, true);
    imageContainer.setContainerType(ContainerType.IMAGE);
    imageContainer.setId(4L);
    imageContainer.setLocationsImageFileProperty(new FileProperty());
    ContainerLocation imageLocation1 = new ContainerLocation(imageContainer);
    imageLocation1.setId(11L);
    imageContainer.getLocations().add(imageLocation1);
    ContainerLocation imageLocation2 = new ContainerLocation(imageContainer);
    imageLocation2.setId(12L);
    imageContainer.getLocations().add(imageLocation2);
    ContainerLocation imageLocation3 = new ContainerLocation(imageContainer);
    imageLocation3.setId(13L);
    imageContainer.getLocations().add(imageLocation3);

    subSample.moveToNewParentAndLocation(imageContainer, imageLocation1);
    assertEquals(imageContainer.getId(), subSample.getParentId());
    assertEquals(imageLocation1.getId(), subSample.getParentLocation().getId());
    movingContainer.moveToNewParentAndLocation(imageContainer, imageLocation2);
    assertEquals(imageContainer.getId(), movingContainer.getParentId());
    assertEquals(imageLocation2.getId(), movingContainer.getParentLocation().getId());
    assertEquals(0, gridContainer6by4.getContentCount());
    assertEquals(2, imageContainer.getContentCount());
    assertEquals(3, imageContainer.getLocations().size());

    // to another location inside image container
    subSample.moveToNewParentAndLocation(imageContainer, imageLocation3);
    assertEquals(imageContainer.getId(), subSample.getParentId());
    assertEquals(imageLocation3.getId(), subSample.getParentLocation().getId());
    movingContainer.moveToNewParentAndLocation(imageContainer, imageLocation1);
    assertEquals(imageContainer.getId(), movingContainer.getParentId());
    assertEquals(imageLocation1.getId(), movingContainer.getParentLocation().getId());
    assertEquals(2, imageContainer.getContentCount());
    assertEquals(3, imageContainer.getLocations().size());

    // back to original list container
    subSample.moveToNewParent(listContainer1);
    assertEquals(listContainer1.getId(), subSample.getParentId());
    movingContainer.moveToNewParent(listContainer1);
    assertEquals(listContainer1.getId(), movingContainer.getParentId());
    assertEquals(2, listContainer1.getContentCount());
    assertEquals(0, imageContainer.getContentCount());
    assertEquals(3, imageContainer.getLocations().size());

    // remove subcontainer and add it again
    movingContainer.removeFromCurrentParent();
    assertNull(movingContainer.getParentId());
    assertEquals(1, listContainer1.getContentCount());
    assertEquals(1, listContainer1.getLocations().size());
    movingContainer.moveToNewParent(listContainer1);
    assertEquals(listContainer1.getId(), movingContainer.getParentId());
    assertEquals(2, listContainer1.getContentCount());
    assertEquals(2, listContainer1.getLocations().size());
  }

  @Test
  void copyListContainer() throws Exception {

    Container listContainer = TestFactory.createListContainer(anyUser);
    listContainer.setCanStoreContainers(false);
    listContainer.setId(1L);
    Container parentContainer = Container.createListContainer(true, true, true);
    parentContainer.addToNewLocation(listContainer);

    SubSample subSample = new SubSample();
    listContainer.addToNewLocation(subSample);
    assertEquals(1, listContainer.getContentCount());
    assertEquals(1, listContainer.getLocations().size());

    Container copy = listContainer.copy(anyUser);

    assertEquals(0, copy.getContentCount());
    assertEquals(0, copy.getLocations().size());
    assertTrue(copy.isCanStoreSamples());
    assertFalse(copy.isCanStoreContainers());
    // copy is unattached, has no parent.
    assertNull(copy.getParentLocation());
    assertNull(copy.getId());
    Set<String> toIgnore = TransformerUtils.toSet("id",
        "activeExtraFields", "extraFields", "activeBarcodes", "barcodes",
        "attachedFiles", "files", "parentLocation", "editInfo", "locations",
        "contentCount", "contentCountSubSamples", "contentCountContainers");

    ModelTestUtils.assertCopiedFieldsAreEqual(copy, listContainer, toIgnore,
        TransformerUtils.toList(Container.class, InventoryRecord.class));
  }

  @Test
  void copyImageContainer() throws Exception {

    Container imgContainer = TestFactory.createImageContainer(anyUser);

    imgContainer.setId(1L);
    Container parentContainer = Container.createListContainer(true, true, true);
    parentContainer.addToNewLocation(imgContainer);

    SubSample subSample = new SubSample();
    imgContainer.getLocations().get(0).setId(5L);
    imgContainer.setRecordInLocation(subSample, imgContainer.getLocations().get(0));
    assertEquals(1, imgContainer.getContentCount());
    assertEquals(2, imgContainer.getLocations().size());
    assertEquals(2, imgContainer.getLocationsCount());

    Container copy = imgContainer.copy(anyUser);
    assertTrue(copy.getName().contains("_COPY"));
    //locations are copied, but are empty
    assertEquals(0, copy.getContentCount());
    assertEquals(2, copy.getLocations().size());
    // container locations are new:
    assertTrue(copy.getLocations().stream().allMatch(cl -> cl.getId() == null));

    // copy is unattached, has no parent.
    assertNull(copy.getParentLocation());
    assertNull(copy.getId());
    Set<String> toIgnore = TransformerUtils.toSet("id",
        "activeExtraFields", "extraFields", "activeBarcodes", "barcodes",
        "attachedFiles", "files", "parentLocation", "editInfo", "locations",
        "contentCount", "contentCountSubSamples", "contentCountContainers");

    ModelTestUtils.assertCopiedFieldsAreEqual(copy, imgContainer, toIgnore,
        TransformerUtils.toList(Container.class, InventoryRecord.class));
  }

  @Test
  void copyGridContainer() throws Exception {
    final int origGridCol = 5;
    final int origGridRow = 8;
    Container gridContainer = TestFactory.createGridContainer(anyUser, origGridCol, origGridRow);
    gridContainer.setId(1L);
    gridContainer.setCanStoreContainers(false);
    Container parentContainer = Container.createListContainer(true, true, true);
    parentContainer.addToNewLocation(gridContainer);

    SubSample subSample = new SubSample();
    gridContainer.addToNewLocationWithCoords(subSample, 3, 6);
    assertEquals(1, gridContainer.getContentCount());
    assertEquals(1, gridContainer.getLocations().size());
    assertEquals(40, gridContainer.getLocationsCount());

    Container copy = gridContainer.copy(anyUser);

    // grid dimensions are copied, but are empty.
    assertEquals(0, copy.getContentCount());
    assertEquals(0, copy.getLocations().size());
    assertEquals(origGridCol, copy.getGridLayoutColumnsNumber());
    assertEquals(origGridRow, copy.getGridLayoutRowsNumber());
    assertTrue(copy.isCanStoreSamples());
    assertFalse(copy.isCanStoreContainers());
    // copy is unattached, has no parent.
    assertNull(copy.getParentLocation());
    assertEquals(40, copy.getLocationsCount());

    Set<String> toIgnore = TransformerUtils.toSet("id", "activeExtraFields",
        "extraFields", "activeBarcodes", "barcodes", "attachedFiles", "files",
        "parentLocation", "editInfo", "locations",
        "contentCount", "contentCountSubSamples", "contentCountContainers");
    ModelTestUtils.assertCopiedFieldsAreEqual(copy, gridContainer, toIgnore,
        TransformerUtils.toList(Container.class, InventoryRecord.class));
  }

  @Test
  void workbenchOperations() throws Exception {

    Container workbench = TestFactory.createWorkbench(anyUser);
    assertEquals(GlobalIdPrefix.BE, workbench.getGlobalIdPrefix());

    Container containerToMove = TestFactory.createListContainer(anyUser);
    containerToMove.setCanStoreContainers(false);
    containerToMove.setId(1L);
    assertNull(containerToMove.getParentContainer());
    assertNull(containerToMove.getLastNonWorkbenchParent());
    assertNull(containerToMove.getLastMoveDate());

    Container topContainer = Container.createListContainer(true, true, true);
    topContainer.setId(2L);

    // move container to top container
    containerToMove.moveToNewParent(topContainer);
    assertEquals(topContainer.getId(), containerToMove.getParentContainer().getId());
    assertNull(containerToMove.getLastNonWorkbenchParent());
    assertNotNull(containerToMove.getLastMoveDate());

    // move to workbench
    containerToMove.moveToNewParent(workbench);
    assertEquals(workbench.getId(), containerToMove.getParentContainer().getId());
    assertEquals(topContainer.getId(), containerToMove.getLastNonWorkbenchParent().getId());
    assertNotNull(containerToMove.getLastMoveDate());

    // move back to be a top one
    containerToMove.removeFromCurrentParent();
    assertNull(containerToMove.getParentContainer());
    assertEquals(topContainer.getId(), containerToMove.getLastNonWorkbenchParent().getId());
    assertNotNull(containerToMove.getLastMoveDate());

    // try moving workbench
    IllegalArgumentException iae = assertThrows(IllegalArgumentException.class,
        () -> workbench.moveToNewParent(topContainer));
    assertEquals("Workbench cannot be moved into other container", iae.getMessage());

    // try copy workbench
    iae = assertThrows(IllegalArgumentException.class, () -> workbench.copy(anyUser));
    assertEquals("Workbench cannot be copied", iae.getMessage());

    // try setting last non-workbench parent to workbench
    iae = assertThrows(IllegalArgumentException.class,
        () -> containerToMove.setLastNonWorkbenchParent(workbench));
    assertEquals("Can't set workbench as lastNonWorkbenchParent", iae.getMessage());

    // try attaching a file to workbench
    iae = assertThrows(IllegalArgumentException.class,
        () -> workbench.addAttachedFile(new InventoryFile(null, null)));
    assertEquals("Can't attach files to Workbench", iae.getMessage());
  }


}
