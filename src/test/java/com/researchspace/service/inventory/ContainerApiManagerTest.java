package com.researchspace.service.inventory;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.axiope.search.SearchUtils;
import com.researchspace.Constants;
import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiBarcode;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiContainerInfo;
import com.researchspace.api.v1.model.ApiContainerInfo.ApiContainerGridLayoutConfig;
import com.researchspace.api.v1.model.ApiContainerLocation;
import com.researchspace.api.v1.model.ApiContainerLocationWithContent;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiExtraField.ExtraFieldTypeEnum;
import com.researchspace.api.v1.model.ApiInventoryEditLock;
import com.researchspace.api.v1.model.ApiInventoryEditLock.ApiInventoryEditLockStatus;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiGroupInfoWithSharedFlag;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventorySharingMode;
import com.researchspace.api.v1.model.ApiInventorySearchResult;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.imageutils.ImageUtils;
import com.researchspace.model.FileProperty;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.events.InventoryAccessEvent;
import com.researchspace.model.events.InventoryCreationEvent;
import com.researchspace.model.events.InventoryDeleteEvent;
import com.researchspace.model.events.InventoryEditingEvent;
import com.researchspace.model.events.InventoryMoveEvent;
import com.researchspace.model.events.InventoryRestoreEvent;
import com.researchspace.model.events.InventoryTransferEvent;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Container.GridLayoutAxisLabelEnum;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.service.impl.ContentInitializerForDevRunManager;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.ws.rs.NotFoundException;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

public class ContainerApiManagerTest extends SpringTransactionalTest {

  private ApplicationEventPublisher mockPublisher;
  User testUser;

  @Before
  public void setUp() {
    testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithExampleContent(testUser);

    // publisher setup must be last in setUp() so that no events are already detected
    mockPublisher = Mockito.mock(ApplicationEventPublisher.class);
    containerApiMgr.setPublisher(mockPublisher);
  }

  @Test
  public void defaultDevProfileContainerRetrieval() {

    // get top containers, with default pagination criteria (name desc ordering)
    PaginationCriteria<Container> pgCrit =
        PaginationCriteria.createDefaultForClass(Container.class);
    ISearchResults<ApiContainerInfo> defaultContainerResult =
        containerApiMgr.getTopContainersForUser(pgCrit, null, null, testUser);
    assertEquals(2, defaultContainerResult.getTotalHits().intValue());
    assertEquals(2, defaultContainerResult.getResults().size());
    assertEquals(
        ContentInitializerForDevRunManager.EXAMPLE_TOP_LIST_CONTAINER_NAME,
        defaultContainerResult.getResults().get(0).getName());
    assertEquals(
        ContentInitializerForDevRunManager.EXAMPLE_TOP_IMAGE_CONTAINER_NAME,
        defaultContainerResult.getResults().get(1).getName());
    assertEquals(0, defaultContainerResult.getPageNumber().intValue());

    // top-containers query is filling additional fields
    ApiContainerInfo listContainerInfo = defaultContainerResult.getResults().get(0);
    assertEquals(null, listContainerInfo.getParentLocation()); // no direct parent for top container
    assertEquals(
        null, listContainerInfo.getParentContainer()); // no direct parent for top container
    assertEquals(testUser.getFullName(), listContainerInfo.getModifiedByFullName());
    assertEquals(3, listContainerInfo.getContentSummary().getTotalCount());
    assertEquals(0, listContainerInfo.getContentSummary().getSubSampleCount());
    assertEquals(3, listContainerInfo.getContentSummary().getContainerCount());
    assertNull(listContainerInfo.getLocationsCount());

    Long listContainerId = listContainerInfo.getId();
    Container topListContainer = containerApiMgr.getContainerById(listContainerId, testUser);
    assertEquals(
        ContentInitializerForDevRunManager.EXAMPLE_TOP_LIST_CONTAINER_NAME,
        topListContainer.getName());
    assertTrue(topListContainer.isListLayoutContainer());
    assertEquals(3, topListContainer.getContentCount());
    Long imageContainerId = defaultContainerResult.getResults().get(1).getId();
    Container topImageContainer = containerApiMgr.getContainerById(imageContainerId, testUser);
    assertEquals(
        ContentInitializerForDevRunManager.EXAMPLE_TOP_IMAGE_CONTAINER_NAME,
        topImageContainer.getName());
    assertNotNull(topImageContainer.getId());
    assertEquals(0, topImageContainer.getContentCount());
    assertNotNull(topImageContainer.getImageFileProperty());
    assertNotNull(topImageContainer.getThumbnailFileProperty());
    assertTrue(topImageContainer.isImageLayoutContainer());
    assertEquals(1, topImageContainer.getAttachedFiles().size());
    assertEquals(
        ContentInitializerForDevRunManager.EXAMPLE_TOP_IMAGE_CONTAINER_ATTACHMENT_NAME,
        topImageContainer.getAttachedFiles().get(0).getFileName());

    // that's somehow still reacheable through spring transactional test, even with lazy
    // initialization
    assertEquals(3, topListContainer.getLocations().size());
    assertEquals(3, topListContainer.getStoredContainers().size());
    Container subcontainer1A = topListContainer.getStoredContainers().get(0);
    assertEquals(3, subcontainer1A.getStoredContainers().size());
    Container subcontainer1B = topListContainer.getStoredContainers().get(1);
    assertEquals(0, subcontainer1B.getStoredContainers().size());
    assertTrue(subcontainer1B.isGridLayoutContainer());
    Container subcontainer1C = topListContainer.getStoredContainers().get(2);
    assertEquals(0, subcontainer1C.getStoredContainers().size());
    assertTrue(subcontainer1B.isGridLayoutContainer());

    // get top containers ordered by global id desc
    pgCrit.setOrderBy(SearchUtils.ORDER_BY_GLOBAL_ID);
    defaultContainerResult = containerApiMgr.getTopContainersForUser(pgCrit, null, null, testUser);
    assertEquals(2, defaultContainerResult.getTotalHits().intValue());
    assertEquals(2, defaultContainerResult.getResults().size());
    assertEquals(
        "4-drawer storage unit (image container)",
        defaultContainerResult.getResults().get(0).getName());
    assertEquals(
        "storage shelf #1 (list container)", defaultContainerResult.getResults().get(1).getName());
    assertEquals(0, defaultContainerResult.getPageNumber().intValue());

    clearSessionAndEvictAll();

    // get specific container by id
    ApiContainer retrievedSubContainer =
        containerApiMgr.getApiContainerById(subcontainer1A.getId(), testUser);
    assertNotNull(retrievedSubContainer);
    assertNotNull(retrievedSubContainer.getId());
    assertEquals("box #1 (list container)", retrievedSubContainer.getName());
    assertEquals(4, retrievedSubContainer.getContentSummary().getTotalCount());
    assertEquals(4, retrievedSubContainer.getLocations().size());

    // stored items should be initialized
    ApiInventoryRecordInfo subSubcontainer =
        retrievedSubContainer.getLocations().get(0).getContent();
    assertNotNull(subSubcontainer);
    assertNotNull(subSubcontainer.getId());
    assertEquals("box A (list container)", subSubcontainer.getName());

    // last item is a subsample
    ApiInventoryRecordInfo subsample = retrievedSubContainer.getLocations().get(3).getContent();
    assertNotNull(subsample);
    assertNotNull(subsample.getId());
    assertEquals("Basic Sample.01", subsample.getName());

    // check subsubcontainer parents
    ApiContainer retrievedSubSubContainer =
        containerApiMgr.getApiContainerById(subSubcontainer.getId(), testUser);
    assertNotNull(retrievedSubSubContainer);
    assertEquals(2, retrievedSubSubContainer.getParentContainers().size());
    assertEquals(
        subcontainer1A.getId(), retrievedSubSubContainer.getParentContainers().get(0).getId());
    assertEquals(
        topListContainer.getId(), retrievedSubSubContainer.getParentContainers().get(1).getId());
  }

  @Test
  public void containerVisibilityWithinGroup() {

    // create new users, and containers
    User pi = createAndSaveUserIfNotExists("pi" + getRandomName(8), Constants.PI_ROLE);
    initialiseContentWithEmptyContent(pi);
    ApiContainer piContainer = createBasicContainerForUser(pi, "pi's cont");
    moveContainerToTopLevel(piContainer, pi);

    // create subsample within pi's container
    ApiSampleWithFullSubSamples piApiSample = new ApiSampleWithFullSubSamples();
    ApiSubSample piSubSample = new ApiSubSample("pi's subsample");
    piSubSample.setParentContainer(piContainer);
    piApiSample.getSubSamples().add(piSubSample);
    sampleApiMgr.createNewApiSample(piApiSample, pi);

    // check visibility within a group
    Group group = createGroup(getRandomName(8), pi);
    addUsersToGroup(pi, group, testUser);

    // create user's subsample within pi's container
    ApiSampleWithFullSubSamples userApiSample = new ApiSampleWithFullSubSamples();
    ApiSubSample userSubSample = new ApiSubSample("user's subsample");
    userSubSample.setParentContainer(piContainer);
    userApiSample.getSubSamples().add(userSubSample);
    sampleApiMgr.createNewApiSample(userApiSample, testUser);

    ISearchResults<ApiContainerInfo> userContainersResult =
        containerApiMgr.getTopContainersForUser(
            PaginationCriteria.createDefaultForClass(Container.class), null, null, testUser);
    assertEquals(3, userContainersResult.getTotalHits());
    assertEquals(
        ContentInitializerForDevRunManager.EXAMPLE_TOP_LIST_CONTAINER_NAME,
        userContainersResult.getResults().get(0).getName());
    ApiContainerInfo piContainerInfo = userContainersResult.getResults().get(1);
    assertEquals("pi's cont", piContainerInfo.getName());
    assertEquals(2, piContainerInfo.getContentSummary().getTotalCount());
    assertEquals(
        ContentInitializerForDevRunManager.EXAMPLE_TOP_IMAGE_CONTAINER_NAME,
        userContainersResult.getResults().get(2).getName());
    assertEquals(2, piContainerInfo.getPermittedActions().size());

    // can limit to just pi's containers
    userContainersResult =
        containerApiMgr.getTopContainersForUser(
            PaginationCriteria.createDefaultForClass(Container.class),
            pi.getUsername(),
            null,
            testUser);
    assertEquals(1, userContainersResult.getTotalHits());
    assertEquals("pi's cont", userContainersResult.getResults().get(0).getName());

    // user can also search content of pi's container
    ApiInventorySearchResult containerContent =
        containerApiMgr.searchForContentOfContainer(
            piContainer.getId(),
            null,
            null,
            PaginationCriteria.createDefaultForClass(InventoryRecord.class),
            testUser);
    assertEquals(2, containerContent.getRecords().size());
    // ... and limit the search to particular owner
    containerContent =
        containerApiMgr.searchForContentOfContainer(
            piContainer.getId(),
            testUser.getUsername(),
            null,
            PaginationCriteria.createDefaultForClass(InventoryRecord.class),
            testUser);
    assertEquals(1, containerContent.getRecords().size());
    assertEquals("user's subsample", containerContent.getRecords().get(0).getName());

    // check visibility as a pi
    ISearchResults<ApiContainerInfo> piContainersResult =
        containerApiMgr.getTopContainersForUser(
            PaginationCriteria.createDefaultForClass(Container.class), null, null, pi);
    assertEquals(3, piContainersResult.getTotalHits());
    assertEquals(
        ContentInitializerForDevRunManager.EXAMPLE_TOP_LIST_CONTAINER_NAME,
        piContainersResult.getResults().get(0).getName());
    assertEquals("pi's cont", piContainersResult.getResults().get(1).getName());
    assertEquals(3, piContainersResult.getResults().get(1).getPermittedActions().size());
    assertEquals(
        ContentInitializerForDevRunManager.EXAMPLE_TOP_IMAGE_CONTAINER_NAME,
        piContainersResult.getResults().get(2).getName());
  }

  @Test
  public void topContainerVisibilityInWhitelistedGroup() {
    // create users and two groups
    User pi1 = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    User pi2 = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi2"), Constants.PI_ROLE);
    User user1 = createAndSaveUserIfNotExists(getRandomAlphabeticString("user1"));
    User user2 = createAndSaveUserIfNotExists(getRandomAlphabeticString("user2"));
    User user3 = createAndSaveUserIfNotExists(getRandomAlphabeticString("user3"));
    User user4 = createAndSaveUserIfNotExists(getRandomAlphabeticString("user4"));
    initialiseContentWithEmptyContent(pi1, pi2, user1, user2, user3, user4);

    Group groupA = createGroup("groupA", pi1);
    addUsersToGroup(pi1, groupA, user1, user2);
    Group groupB = createGroup("groupB", pi2);
    addUsersToGroup(pi2, groupB, user3, user4);

    // create top-level containers owned by user1, whitelisted just for groupB
    ApiContainer user1Container = createBasicContainerForUser(user1, "user1 cont", List.of(groupB));
    assertNotNull(user1Container.getGlobalId());
    assertEquals(ApiInventorySharingMode.WHITELIST, user1Container.getSharingMode());
    moveContainerToTopLevel(user1Container, user1);
    // create top-level container owned by user3, group-shared by default
    ApiContainer user3Container = createBasicContainerForUser(user3, "user3 cont");
    assertNotNull(user3Container.getGlobalId());
    assertEquals(ApiInventorySharingMode.OWNER_GROUPS, user3Container.getSharingMode());
    moveContainerToTopLevel(user3Container, user3);
    // create top-level container owned by user4, whitelisted as private
    ApiContainer user4Container =
        createBasicContainerForUser(user4, "user4 private cont", List.of());
    assertNotNull(user4Container.getGlobalId());
    assertEquals(ApiInventorySharingMode.WHITELIST, user4Container.getSharingMode());
    moveContainerToTopLevel(user4Container, user4);

    // user1 should find own container
    ISearchResults<ApiContainerInfo> topContainerResults =
        containerApiMgr.getTopContainersForUser(
            PaginationCriteria.createDefaultForClass(Container.class), null, null, user1);
    assertEquals(1, topContainerResults.getTotalHits());
    assertEquals(
        user1Container.getGlobalId(), topContainerResults.getResults().get(0).getGlobalId());

    // user2 is in groupA, should see no containers
    topContainerResults =
        containerApiMgr.getTopContainersForUser(
            PaginationCriteria.createDefaultForClass(Container.class), null, null, user2);
    assertEquals(0, topContainerResults.getTotalHits());

    // user3 is in groupB, should see own container and user1's which is whitelisted for groupB
    topContainerResults =
        containerApiMgr.getTopContainersForUser(
            PaginationCriteria.createDefaultForClass(Container.class), null, null, user3);
    assertEquals(2, topContainerResults.getTotalHits());
    assertEquals(
        user3Container.getGlobalId(), topContainerResults.getResults().get(0).getGlobalId());
    assertEquals(
        user1Container.getGlobalId(), topContainerResults.getResults().get(1).getGlobalId());

    // user4 is in groupB, should see own private container, user3's which is group-shared, and
    // user1's which is whitelisted for groupB
    topContainerResults =
        containerApiMgr.getTopContainersForUser(
            PaginationCriteria.createDefaultForClass(Container.class), null, null, user4);
    assertEquals(3, topContainerResults.getTotalHits());
    assertEquals(
        user4Container.getGlobalId(), topContainerResults.getResults().get(0).getGlobalId());
    assertEquals(
        user3Container.getGlobalId(), topContainerResults.getResults().get(1).getGlobalId());
    assertEquals(
        user1Container.getGlobalId(), topContainerResults.getResults().get(2).getGlobalId());

    // pi1 should see user1's container, because they are the user1's PI
    topContainerResults =
        containerApiMgr.getTopContainersForUser(
            PaginationCriteria.createDefaultForClass(Container.class), null, null, pi1);
    assertEquals(1, topContainerResults.getTotalHits());
    assertEquals(
        user1Container.getGlobalId(), topContainerResults.getResults().get(0).getGlobalId());

    // pi2 should see user3's and user4's container, because they are PI of their group, and user1's
    // container, as it's whitelisted
    topContainerResults =
        containerApiMgr.getTopContainersForUser(
            PaginationCriteria.createDefaultForClass(Container.class), null, null, pi2);
    assertEquals(3, topContainerResults.getTotalHits());
    assertEquals(
        user4Container.getGlobalId(), topContainerResults.getResults().get(0).getGlobalId());
    assertEquals(
        user3Container.getGlobalId(), topContainerResults.getResults().get(1).getGlobalId());
    assertEquals(
        user1Container.getGlobalId(), topContainerResults.getResults().get(2).getGlobalId());
  }

  @Test
  public void containerDuplicationWithinGroup() {
    // create pi with a container, and a group with test user
    User pi = createAndSaveUserIfNotExists("pi" + getRandomName(8), Constants.PI_ROLE);
    initialiseContentWithEmptyContent(pi);
    ApiContainer piContainer = createBasicContainerForUser(pi, "pi's cont");
    moveContainerToTopLevel(piContainer, pi);
    Group group = createGroup("group", pi);
    addUsersToGroup(pi, group, testUser);

    // user can duplicate pi's container, copy will be placed at user's workbench
    ApiContainer testUserWorkbench = getWorkbenchForUser(testUser);
    ApiContainer copiedPiContainer = containerApiMgr.duplicate(piContainer.getId(), testUser);
    assertEquals(
        testUserWorkbench.getGlobalId(), copiedPiContainer.getParentContainer().getGlobalId());
    assertEquals("pi's cont_COPY", copiedPiContainer.getName());
    assertEquals(testUser.getUsername(), copiedPiContainer.getCreatedBy());
    assertEquals(testUser.getUsername(), copiedPiContainer.getModifiedBy());
    assertEquals(
        testUser.getUsername(), copiedPiContainer.getOwner().getUsername()); // user owns the copy
  }

  @Test
  public void containerVisibilityOutsideGroup() {

    // create a pi
    User pi = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    initialiseContentWithEmptyContent(pi);

    // create user who'll be moved outside group, with subsample inside pi's container
    User otherUser = createAndSaveUserIfNotExists("other");
    initialiseContentWithEmptyContent(otherUser);

    // create a group
    Group group = createGroup("group", pi);
    addUsersToGroup(pi, group, testUser, otherUser);

    // create two containers, and user's subsample inside one of them
    ApiContainer piContainer1 = createBasicContainerForUser(pi, "pi's cont 1");
    ApiContainer piContainer2 = createBasicContainerForUser(pi, "pi's cont 2");

    ApiSampleWithFullSubSamples userApiSample = new ApiSampleWithFullSubSamples();
    ApiSubSample userSubSample = new ApiSubSample("user's subsample");
    userSubSample.setParentContainer(piContainer1);
    userApiSample.getSubSamples().add(userSubSample);
    sampleApiMgr.createNewApiSample(userApiSample, testUser);

    // create other user's subsample in one of pi's containers
    ApiSampleWithFullSubSamples otherApiSample = new ApiSampleWithFullSubSamples();
    ApiSubSample otherSubSample = new ApiSubSample("other user's subsample");
    otherSubSample.setParentContainer(piContainer1);
    otherApiSample.getSubSamples().add(otherSubSample);
    sampleApiMgr.createNewApiSample(otherApiSample, otherUser);

    // remove other user from group
    logoutAndLoginAs(pi);
    grpMgr.removeUserFromGroup(otherUser.getUsername(), group.getId(), pi);

    /*
     *  check visibility for "pi" user
     */

    // pi can see both subsamples when querying the container content
    ApiInventorySearchResult containerContent =
        containerApiMgr.searchForContentOfContainer(
            piContainer1.getId(),
            null,
            null,
            PaginationCriteria.createDefaultForClass(InventoryRecord.class),
            pi);
    assertEquals(2, containerContent.getTotalHits());
    assertEquals("user's subsample", containerContent.getRecords().get(0).getName());
    assertEquals("other user's subsample", containerContent.getRecords().get(1).getName());
    // pi can query for full details of either subsample
    ApiSubSample retrievedTestUserSubSample =
        subSampleApiMgr.getApiSubSampleById(containerContent.getRecords().get(0).getId(), pi);
    assertEquals("user's subsample", retrievedTestUserSubSample.getName());
    ApiSubSample retrievedOtherUserSubSample =
        subSampleApiMgr.getApiSubSampleById(containerContent.getRecords().get(1).getId(), pi);
    assertEquals("other user's subsample", retrievedOtherUserSubSample.getName());
    // pi can also query details of testUser's sample
    ApiSample retrievedTestUserSample =
        sampleApiMgr.getApiSampleById(retrievedTestUserSubSample.getSampleInfo().getId(), pi);
    assertEquals("Generic Sample", retrievedTestUserSample.getName());
    assertFalse(retrievedTestUserSample.isClearedForLimitedView());
    // but for other user's sample they'll just get limited view details
    ApiSample retrievedOtherUserSample =
        sampleApiMgr.getApiSampleById(retrievedOtherUserSubSample.getSampleInfo().getId(), pi);
    assertTrue(retrievedOtherUserSample.isClearedForLimitedView());

    /*
     *  check visibility for "other user"
     */

    // other user can access the container that stores their subsample
    ApiContainer retrievedPiContainer =
        containerApiMgr.getApiContainerById(piContainer1.getId(), otherUser);
    assertEquals("pi's cont 1", retrievedPiContainer.getName());
    assertEquals(1, retrievedPiContainer.getPermittedActions().size());
    // can query that container's content, but just get limited view
    ApiInventorySearchResult containerContentSeenByOtherUser =
        containerApiMgr.searchForContentOfContainer(
            piContainer1.getId(),
            null,
            null,
            PaginationCriteria.createDefaultForClass(InventoryRecord.class),
            otherUser);
    assertEquals(2, containerContentSeenByOtherUser.getTotalHits());
    assertEquals("user's subsample", containerContentSeenByOtherUser.getRecords().get(0).getName());
    assertTrue(containerContentSeenByOtherUser.getRecords().get(0).isClearedForLimitedView());
    assertEquals(
        "other user's subsample", containerContentSeenByOtherUser.getRecords().get(1).getName());
    assertFalse(containerContentSeenByOtherUser.getRecords().get(1).isClearedForLimitedView());

    // other user also can access only public details of container belonging to pi
    ApiContainer containerAsSeenByOtherUser =
        containerApiMgr.getApiContainerById(piContainer2.getId(), otherUser);
    assertTrue(containerAsSeenByOtherUser.isClearedForPublicView());
    // copy action is also blocked
    NotFoundException nfe =
        assertThrows(
            NotFoundException.class,
            () -> containerApiMgr.duplicate(piContainer2.getId(), otherUser));
    assertTrue(
        nfe.getMessage().contains("does not exist, or you do not have permission to access it"));
  }

  @Test
  public void saveNewApiContainersAndContent() throws IOException {

    ISearchResults<ApiContainerInfo> userContainersResult =
        containerApiMgr.getTopContainersForUser(
            PaginationCriteria.createDefaultForClass(Container.class), null, null, testUser);
    final int intitalCount = userContainersResult.getHits().intValue();

    // create new default container
    ApiContainer toCreate = new ApiContainer();
    final String containerName = getRandomAlphabeticString("cont");
    toCreate.setName(containerName);
    toCreate.setRemoveFromParentContainerRequest(true);
    ApiContainer savedContainer = containerApiMgr.createNewApiContainer(toCreate, testUser);
    verifyCreationEventPublished();

    // create custom sub container inside default container
    ApiContainer newCustomContainer = new ApiContainer();
    newCustomContainer.setName("test name");
    newCustomContainer.setDescription("test description");
    newCustomContainer.setApiTagInfo("test tags");
    List<ApiExtraField> extraFields = new ArrayList<>();
    ApiExtraField extraField = new ApiExtraField();
    extraField.setContent("test extra field content");
    extraFields.add(extraField);
    newCustomContainer.setExtraFields(extraFields);
    newCustomContainer.setParentContainer(savedContainer);
    ApiContainer savedCustomContainer =
        containerApiMgr.createNewApiContainer(newCustomContainer, testUser);
    Mockito.verify(mockPublisher, Mockito.times(2))
        .publishEvent(Mockito.any(InventoryCreationEvent.class));
    Mockito.verify(mockPublisher).publishEvent(Mockito.any(InventoryMoveEvent.class));

    // create subcontainer inside specific locations of a custom container
    ApiContainer newApiSubContainer = new ApiContainer();
    newApiSubContainer.setName("subcontainer");
    newApiSubContainer.setParentContainer(savedCustomContainer);
    containerApiMgr.createNewApiContainer(newApiSubContainer, testUser);
    Mockito.verify(mockPublisher, Mockito.times(3))
        .publishEvent(Mockito.any(InventoryCreationEvent.class));
    Mockito.verify(mockPublisher, Mockito.times(2))
        .publishEvent(Mockito.any(InventoryMoveEvent.class));

    // create (sub)sample inside specific locations of a custom container
    ApiSampleWithFullSubSamples newApiSample = new ApiSampleWithFullSubSamples();
    ApiSubSample newSubSample = new ApiSubSample();
    newSubSample.setParentContainer(savedCustomContainer);
    newApiSample.getSubSamples().add(newSubSample);
    sampleApiMgr.createNewApiSample(newApiSample, testUser);

    // verify created hierarchy
    userContainersResult =
        containerApiMgr.getTopContainersForUser(
            PaginationCriteria.createDefaultForClass(Container.class), null, null, testUser);
    assertEquals(intitalCount + 1, userContainersResult.getHits().intValue());

    Long defaultContainerId =
        userContainersResult.getResults().stream()
            .filter(c -> c.getName().equals(savedContainer.getName()))
            .findFirst()
            .get()
            .getId();
    Container retrievedDefaultContainer =
        containerApiMgr.getContainerById(defaultContainerId, testUser);
    Long retrievedContainerId = retrievedDefaultContainer.getId();
    assertEquals(savedContainer.getId(), retrievedContainerId);
    assertEquals(containerName, retrievedDefaultContainer.getName());
    assertNull(retrievedDefaultContainer.getImageFileProperty());
    assertNull(retrievedDefaultContainer.getThumbnailFileProperty());
    assertEquals(1, retrievedDefaultContainer.getContentCount());
    assertEquals(1, retrievedDefaultContainer.getLocations().size()); // auto-created one
    assertEquals(1, retrievedDefaultContainer.getStoredContainers().size());

    Container retrievedCustomContainer = retrievedDefaultContainer.getStoredContainers().get(0);
    Long retrievedCustomContainerId = retrievedCustomContainer.getId();
    assertEquals(savedCustomContainer.getId(), retrievedCustomContainerId);
    assertEquals(newCustomContainer.getName(), retrievedCustomContainer.getName());
    assertEquals(newCustomContainer.getDescription(), retrievedCustomContainer.getDescription());
    assertEquals(newCustomContainer.getDBStringFromTags(), retrievedCustomContainer.getTags());
    assertEquals(1, retrievedCustomContainer.getActiveExtraFields().size());
    assertEquals(2, retrievedCustomContainer.getLocations().size());
    assertEquals(2, retrievedCustomContainer.getContentCount());

    Container retrievedSubContainer = retrievedCustomContainer.getStoredContainers().get(0);
    assertEquals("subcontainer", retrievedSubContainer.getName());
    assertTrue(retrievedSubContainer.isCanStoreSamples());
    assertTrue(retrievedSubContainer.isCanStoreContainers());
    SubSample retrievedSubSample = retrievedCustomContainer.getStoredSubSamples().get(0);
    assertEquals("Generic Sample.01", retrievedSubSample.getName());

    Mockito.verifyNoMoreInteractions(mockPublisher);
  }

  @Test
  public void updateContainer() throws IOException, InterruptedException {

    // create new default container
    ApiContainer savedContainer =
        containerApiMgr.createNewApiContainer(new ApiContainer(), testUser);
    verifyCreationEventPublished();

    ApiContainer retrievedLocationsContainer =
        containerApiMgr.getApiContainerById(savedContainer.getId(), testUser);
    Mockito.verify(mockPublisher).publishEvent(Mockito.any(InventoryAccessEvent.class));

    // basic fields
    ApiContainer containerUpdates = new ApiContainer();
    containerUpdates.setId(savedContainer.getId());
    containerUpdates.setName("updated name");
    containerUpdates.setDescription("updated description");
    containerUpdates.setApiTagInfo("updated tags");

    // add extra numeric field
    ApiExtraField extraNumeric = new ApiExtraField();
    extraNumeric.setType(ExtraFieldTypeEnum.NUMBER);
    extraNumeric.setContent("3.14");
    extraNumeric.setNewFieldRequest(true);
    containerUpdates.setExtraFields(List.of(extraNumeric));

    // add barcode
    ApiBarcode newBarcode = new ApiBarcode();
    newBarcode.setData("B123");
    newBarcode.setNewBarcodeRequest(true);
    containerUpdates.setBarcodes(List.of(newBarcode));

    Thread.sleep(10); // ensure it's later
    containerApiMgr.updateApiContainer(containerUpdates, testUser);
    Mockito.verify(mockPublisher).publishEvent(Mockito.any(InventoryEditingEvent.class));

    ApiContainer updatedContainer =
        containerApiMgr.getApiContainerById(savedContainer.getId(), testUser);
    assertNotNull(updatedContainer);
    assertEquals(retrievedLocationsContainer.getGlobalId(), updatedContainer.getGlobalId());
    assertEquals("updated name", updatedContainer.getName());
    assertEquals("updated description", updatedContainer.getDescription());
    assertEquals("updated tags", updatedContainer.getDBStringFromTags());
    assertTrue(updatedContainer.isListContainer());
    assertTrue(updatedContainer.getCanStoreContainers());
    assertTrue(
        updatedContainer.getLastModifiedMillis()
            > retrievedLocationsContainer.getLastModifiedMillis());
    assertEquals(testUser.getFullName(), updatedContainer.getModifiedByFullName());
    assertEquals(1, updatedContainer.getExtraFields().size());
    assertEquals(1, updatedContainer.getBarcodes().size());
    assertEquals(0, updatedContainer.getLocations().size());
    Mockito.verify(mockPublisher, Mockito.times(2))
        .publishEvent(Mockito.any(InventoryAccessEvent.class));

    // change into grid container, with non-default axis labels
    containerUpdates = new ApiContainer();
    containerUpdates.setId(updatedContainer.getId());
    containerUpdates.setGridLayout(
        new ApiContainerGridLayoutConfig(
            12, 24, GridLayoutAxisLabelEnum.ABC, GridLayoutAxisLabelEnum.N123));
    containerApiMgr.updateApiContainer(containerUpdates, testUser);
    Mockito.verify(mockPublisher, Mockito.times(2))
        .publishEvent(Mockito.any(InventoryEditingEvent.class));

    updatedContainer = containerApiMgr.getApiContainerById(savedContainer.getId(), testUser);
    assertEquals(0, updatedContainer.getLocations().size());
    assertTrue(updatedContainer.isGridContainer());
    assertTrue(updatedContainer.getCanStoreContainers());
    assertEquals(12, updatedContainer.getGridLayout().getColumnsNumber());
    assertEquals(24, updatedContainer.getGridLayout().getRowsNumber());
    assertEquals(
        GridLayoutAxisLabelEnum.ABC, updatedContainer.getGridLayout().getColumnsLabelType());
    assertEquals(GridLayoutAxisLabelEnum.N123, updatedContainer.getGridLayout().getRowsLabelType());
    Mockito.verify(mockPublisher, Mockito.times(3))
        .publishEvent(Mockito.any(InventoryAccessEvent.class));

    Mockito.verifyNoMoreInteractions(mockPublisher);
  }

  @Test
  public void updateImageContainerLocations() throws IOException, InterruptedException {

    // create new image container to check location modifications
    ApiContainer newImageContainer = createBasicImageContainerForUser(testUser);
    verifyCreationEventPublished();

    // save image container
    Container savedLocationsContainer =
        containerApiMgr.getContainerById(newImageContainer.getId(), testUser);
    assertTrue(savedLocationsContainer.isImageLayoutContainer());
    assertEquals(2, savedLocationsContainer.getLocations().size());
    assertEquals(2, savedLocationsContainer.getLocationsCount());
    ApiContainer retrievedLocationsContainer =
        containerApiMgr.getApiContainerById(savedLocationsContainer.getId(), testUser);
    Mockito.verify(mockPublisher).publishEvent(Mockito.any(InventoryAccessEvent.class));

    // check saved locations
    ApiContainerLocationWithContent createdLocation1 =
        retrievedLocationsContainer.getLocations().get(0);
    assertEquals(15, createdLocation1.getCoordX());
    assertEquals(25, createdLocation1.getCoordY());
    ApiContainerLocationWithContent createdLocation2 =
        retrievedLocationsContainer.getLocations().get(1);
    assertEquals(25, createdLocation2.getCoordX());
    assertEquals(35, createdLocation2.getCoordY());

    // update locations
    ApiContainer containerUpdates = new ApiContainer();

    // modify first location, delete second one
    containerUpdates.setId(retrievedLocationsContainer.getId());
    final int UPDATED_COORD_X = 11;
    final int UPDATED_COORD_Y = 21;
    createdLocation1.setCoordX(UPDATED_COORD_X);
    createdLocation1.setCoordY(UPDATED_COORD_Y);
    createdLocation2.setDeleteLocationRequest(true);
    containerUpdates.setLocations(Arrays.asList(createdLocation1, createdLocation2));
    containerApiMgr.updateApiContainer(containerUpdates, testUser);
    Mockito.verify(mockPublisher).publishEvent(Mockito.any(InventoryEditingEvent.class));

    ApiContainer updatedContainer =
        containerApiMgr.getApiContainerById(savedLocationsContainer.getId(), testUser);
    assertEquals(1, updatedContainer.getLocations().size());
    assertEquals(1, savedLocationsContainer.getLocationsCount());
    ApiContainerLocationWithContent updatedLocation = updatedContainer.getLocations().get(0);
    assertEquals(UPDATED_COORD_X, updatedLocation.getCoordX());
    assertEquals(UPDATED_COORD_Y, updatedLocation.getCoordY());
    Mockito.verify(mockPublisher, Mockito.times(2))
        .publishEvent(Mockito.any(InventoryAccessEvent.class));

    ApiInventorySearchResult containerChildren =
        containerApiMgr.searchForContentOfContainer(
            savedLocationsContainer.getId(),
            null,
            null,
            PaginationCriteria.createDefaultForClass(InventoryRecord.class),
            testUser);
    assertEquals(0, containerChildren.getTotalHits());

    Mockito.verifyNoMoreInteractions(mockPublisher);
  }

  @Test
  public void verifyUpdateErrors() {

    // create new image container to check location modifications
    ApiContainer newImageContainer = createBasicImageContainerForUser(testUser);
    assertTrue(newImageContainer.getCanStoreSamples());
    assertTrue(newImageContainer.getCanStoreContainers());
    Container savedImageContainer =
        containerApiMgr.getContainerById(newImageContainer.getId(), testUser);

    /*
     *  verify incomplete location update request is rejected
     */
    ApiContainer incorrectUpdate = new ApiContainer();
    ApiContainerLocationWithContent incompleteLocation =
        new ApiContainerLocationWithContent(5, null);
    incompleteLocation.setNewLocationRequest(true);
    incorrectUpdate.setId(savedImageContainer.getId());
    incorrectUpdate.getLocations().add(incompleteLocation);
    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> containerApiMgr.updateApiContainer(incorrectUpdate, testUser));
    assertEquals("The validated object is null", iae.getMessage());

    ApiContainer updatedImageContainer =
        containerApiMgr.getApiContainerById(savedImageContainer.getId(), testUser);
    assertEquals(2, updatedImageContainer.getLocations().size());

    /*
     * change content flag of a container that already has given content
     */
    // place a subcontainer inside image container
    ApiContainer movingContainer = createBasicContainerForUser(testUser);
    ApiContainer moveUpdate = new ApiContainer();
    moveUpdate.setId(movingContainer.getId());
    moveUpdate.setParentLocation(updatedImageContainer.getLocations().get(0));
    movingContainer = containerApiMgr.updateApiContainer(moveUpdate, testUser);
    assertEquals(
        updatedImageContainer.getGlobalId(), movingContainer.getParentContainer().getGlobalId());

    // place a subsample in another location
    ApiSampleWithFullSubSamples newApiSample = new ApiSampleWithFullSubSamples();
    ApiSubSample newSubSample = new ApiSubSample();
    newSubSample.setParentLocation(updatedImageContainer.getLocations().get(1));
    newApiSample.getSubSamples().add(newSubSample);
    sampleApiMgr.createNewApiSample(newApiSample, testUser);

    // try updating parent container so it can't hold subsamples/subscontainers
    ApiContainer storedContentUpdate = new ApiContainer();
    storedContentUpdate.setId(updatedImageContainer.getId());
    storedContentUpdate.setCanStoreContainers(false);
    iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> containerApiMgr.updateApiContainer(storedContentUpdate, testUser));
    assertEquals(
        "Cannot set canStoreContainers to false, as this container is already storing"
            + " subcontainers",
        iae.getMessage());
    storedContentUpdate.setCanStoreContainers(true);
    storedContentUpdate.setCanStoreSamples(false);
    iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> containerApiMgr.updateApiContainer(storedContentUpdate, testUser));
    assertEquals(
        "Cannot set canStoreSamples to false, as this container is already storing subsamples",
        iae.getMessage());
  }

  @Test
  public void saveNewContainerWithImage() throws IOException {
    // create new default container
    ApiContainer savedContainer =
        containerApiMgr.createNewApiContainer(new ApiContainer(), testUser);
    Long containerId = savedContainer.getId();
    verifyCreationEventPublished();

    // update the container with main image (jpg)
    InputStream imageIS = getClass().getResourceAsStream("/StartUpData/inventory/storage_unit.jpg");
    String jpgBase64 =
        ImageUtils.getBase64DataImageFromImageBytes(IOUtils.toByteArray(imageIS), "jpg");

    ApiContainer mainImageUpdateReq = new ApiContainer();
    mainImageUpdateReq.setId(containerId);
    mainImageUpdateReq.setNewBase64Image(jpgBase64);
    containerApiMgr.updateApiContainer(mainImageUpdateReq, testUser);
    Mockito.verify(mockPublisher).publishEvent(Mockito.any(InventoryEditingEvent.class));

    // update the container with locations image (png)
    InputStream locationsImageIS =
        getClass().getResourceAsStream("/StartUpData/inventory/storage_unit_locations.png");
    String pngBase64 =
        ImageUtils.getBase64DataImageFromImageBytes(IOUtils.toByteArray(locationsImageIS), "png");

    ApiContainer locationsImageUpdateReq = new ApiContainer();
    locationsImageUpdateReq.setId(containerId);
    locationsImageUpdateReq.setNewBase64LocationsImage(pngBase64);
    containerApiMgr.updateApiContainer(locationsImageUpdateReq, testUser);
    Mockito.verify(mockPublisher, Mockito.times(2))
        .publishEvent(Mockito.any(InventoryEditingEvent.class));

    // images are named as a hash of their contentsq
    String mainImageHash = "f80c29ef57845fbcece93142a9497afe943cf3772e8941f27beba4a38f51c8df";
    String thumbnailHash = "e7bdf239dd7ea19b3e2180de4ca1de09816ef51f15ba6fbe3a6c6003c42e2f72";
    String locationsHash = "28fcb66876c91f599e63aa4daa19f18d5db80c82caeb3add283348fcfaaea800";

    // verify both images saved
    Container updatedContainer = containerApiMgr.getContainerById(containerId, testUser);
    FileProperty jpgImageFP = updatedContainer.getImageFileProperty();
    assertNotNull(jpgImageFP);
    assertEquals("102469", jpgImageFP.getFileSize());
    assertEquals(mainImageHash + ".jpg", jpgImageFP.getFileName());
    FileProperty jpgThumbnailFP = updatedContainer.getThumbnailFileProperty();
    assertNotNull(jpgThumbnailFP);
    assertEquals("3177", jpgThumbnailFP.getFileSize());
    assertEquals(thumbnailHash + "_thumbnail.jpg", jpgThumbnailFP.getFileName());
    FileProperty pngLocationsFP = updatedContainer.getLocationsImageFileProperty();
    assertNotNull(pngLocationsFP);
    assertEquals("168434", pngLocationsFP.getFileSize());
    assertEquals(locationsHash + ".png", pngLocationsFP.getFileName());

    // update main image again
    // save the container with jpeg image
    byte[] updatedJpgBytes = RSpaceTestUtils.getResourceAsByteArray("smartscotland3.jpg");
    String updatedJpgBase64 = ImageUtils.getBase64DataImageFromImageBytes(updatedJpgBytes, "jpg");
    ApiContainer mainImageUpdateReq2 = new ApiContainer();
    mainImageUpdateReq2.setId(containerId);
    mainImageUpdateReq2.setNewBase64Image(updatedJpgBase64);
    containerApiMgr.updateApiContainer(mainImageUpdateReq2, testUser);
    Mockito.verify(mockPublisher, Mockito.times(3))
        .publishEvent(Mockito.any(InventoryEditingEvent.class));

    // verify image updated
    updatedContainer = containerApiMgr.getContainerById(containerId, testUser);

    FileProperty jpgUpdatedImageFP = updatedContainer.getImageFileProperty();
    String updatedMainImageHash =
        "21ede13b2a6e043c956e1e7f14f934bbdd6c8c3d5589cc80bcfdc09c86045f49";
    assertNotNull(jpgUpdatedImageFP);
    assertEquals("794", jpgUpdatedImageFP.getFileSize());
    assertEquals(updatedMainImageHash + ".jpg", jpgUpdatedImageFP.getFileName());
    assertEquals(updatedMainImageHash, jpgUpdatedImageFP.getContentsHash());

    FileProperty jpgUpdatedThumbnailFP = updatedContainer.getThumbnailFileProperty();
    String updatedThumbnailHash =
        "d34f2fe4a6b04eb4b5ddc1d1273ff0064042caf0cb114828b5a4f336d2203958";
    assertNotNull(jpgUpdatedThumbnailFP);
    assertEquals("972", jpgUpdatedThumbnailFP.getFileSize());
    assertEquals(updatedThumbnailHash + "_thumbnail.jpg", jpgUpdatedThumbnailFP.getFileName());
    // locations image should stay the same
    assertEquals(pngLocationsFP, updatedContainer.getLocationsImageFileProperty());

    Mockito.verifyNoMoreInteractions(mockPublisher);
  }

  @Test
  public void saveIntoContainerErrors() throws Exception {

    // cannot save into the occupied location
    ApiContainer savedContainer = createBasicImageContainerForUser(testUser);
    ApiContainerLocation imageLocation = savedContainer.getLocations().get(0);

    // subcontainer can be created fine
    ApiContainer newSubContainer = new ApiContainer();
    newSubContainer.setParentLocation(imageLocation);
    containerApiMgr.createNewApiContainer(newSubContainer, testUser);

    // ... but not the second one with the same location
    ApiContainer otherSubContainer = new ApiContainer();
    otherSubContainer.setParentLocation(imageLocation);
    try {
      containerApiMgr.createNewApiContainer(otherSubContainer, testUser);
      fail("was able to save into occupied location");
    } catch (IllegalArgumentException iae) {
      assertTrue(iae.getMessage().contains("is already taken by the record: IC"), iae.getMessage());
    }
  }

  @Test
  public void moveContainerIntoAnotherContainer() throws Exception {

    int expectedCreatedEventsCount = 0;
    int expectedMovedEventsCount = 0;
    int expectedAccessedEventsCount = 0;
    int expectedDeletedEventsCount = 0;

    Mockito.verify(mockPublisher, Mockito.times(expectedCreatedEventsCount))
        .publishEvent(Mockito.any(InventoryCreationEvent.class));
    Mockito.verify(mockPublisher, Mockito.times(expectedMovedEventsCount))
        .publishEvent(Mockito.any(InventoryMoveEvent.class));
    Mockito.verify(mockPublisher, Mockito.times(expectedAccessedEventsCount))
        .publishEvent(Mockito.any(InventoryAccessEvent.class));
    Mockito.verify(mockPublisher, Mockito.times(expectedDeletedEventsCount))
        .publishEvent(Mockito.any(InventoryDeleteEvent.class));

    ApiContainer listContainer = createBasicContainerForUser(testUser);
    expectedCreatedEventsCount++;
    ApiContainer imageContainer = createBasicImageContainerForUser(testUser);
    expectedCreatedEventsCount++;

    // subcontainer to move around
    ApiContainer subContainer = createBasicContainerForUser(testUser, "test subcontainer");
    assertTrue(subContainer.getParentContainer().isWorkbench());
    expectedCreatedEventsCount++;
    Mockito.verify(mockPublisher, Mockito.times(expectedCreatedEventsCount))
        .publishEvent(Mockito.any(InventoryCreationEvent.class));

    // move to list container
    ApiContainer updatedSubContainer =
        moveContainerIntoListContainer(subContainer.getId(), listContainer.getId(), testUser);
    expectedMovedEventsCount++;
    assertEquals(listContainer.getId(), updatedSubContainer.getParentContainer().getId());
    Mockito.verify(mockPublisher, Mockito.times(expectedMovedEventsCount))
        .publishEvent(Mockito.any(InventoryMoveEvent.class));

    // verify target container updated
    listContainer = containerApiMgr.getApiContainerById(listContainer.getId(), testUser);
    expectedAccessedEventsCount++;
    assertEquals(1, listContainer.getContentSummary().getTotalCount());
    Mockito.verify(mockPublisher, Mockito.times(expectedAccessedEventsCount))
        .publishEvent(Mockito.any(InventoryAccessEvent.class));

    // remove from parent container
    ApiContainer updateRequest = new ApiContainer();
    updateRequest.setId(subContainer.getId());
    updateRequest.setRemoveFromParentContainerRequest(true);
    updatedSubContainer = containerApiMgr.updateApiContainer(updateRequest, testUser);
    expectedMovedEventsCount++;
    assertNull(updatedSubContainer.getParentContainer());
    Mockito.verify(mockPublisher, Mockito.times(expectedMovedEventsCount))
        .publishEvent(Mockito.any(InventoryMoveEvent.class));

    // verify target container updated
    listContainer = containerApiMgr.getApiContainerById(listContainer.getId(), testUser);
    expectedAccessedEventsCount++;
    assertEquals(0, listContainer.getContentSummary().getTotalCount());
    Mockito.verify(mockPublisher, Mockito.times(expectedAccessedEventsCount))
        .publishEvent(Mockito.any(InventoryAccessEvent.class));

    // move to image container
    updateRequest.setRemoveFromParentContainerRequest(false);
    updateRequest.setParentContainer(imageContainer);
    updateRequest.setParentLocation(imageContainer.getLocations().get(0));
    updatedSubContainer = containerApiMgr.updateApiContainer(updateRequest, testUser);
    expectedMovedEventsCount++;
    assertEquals(imageContainer.getId(), updatedSubContainer.getParentContainer().getId());
    Mockito.verify(mockPublisher, Mockito.times(expectedMovedEventsCount))
        .publishEvent(Mockito.any(InventoryMoveEvent.class));

    // verify target containers updated
    imageContainer = containerApiMgr.getApiContainerById(imageContainer.getId(), testUser);
    expectedAccessedEventsCount++;
    assertEquals(1, imageContainer.getContentSummary().getTotalCount());
    Mockito.verify(mockPublisher, Mockito.times(expectedAccessedEventsCount))
        .publishEvent(Mockito.any(InventoryAccessEvent.class));

    // move back to list container
    updateRequest.setParentContainer(listContainer);
    updateRequest.setParentLocation(null);
    updatedSubContainer = containerApiMgr.updateApiContainer(updateRequest, testUser);
    expectedMovedEventsCount++;
    assertEquals(listContainer.getId(), updatedSubContainer.getParentContainer().getId());
    Mockito.verify(mockPublisher, Mockito.times(expectedMovedEventsCount))
        .publishEvent(Mockito.any(InventoryMoveEvent.class));

    // verify source and target containers updated
    listContainer = containerApiMgr.getApiContainerById(listContainer.getId(), testUser);
    expectedAccessedEventsCount++;
    assertEquals(1, listContainer.getContentSummary().getTotalCount());
    imageContainer = containerApiMgr.getApiContainerById(imageContainer.getId(), testUser);
    expectedAccessedEventsCount++;
    assertEquals(0, imageContainer.getContentSummary().getTotalCount());
    Mockito.verify(mockPublisher, Mockito.times(expectedAccessedEventsCount))
        .publishEvent(Mockito.any(InventoryAccessEvent.class));

    // delete subcontainer
    subContainer = containerApiMgr.markContainerAsDeleted(subContainer.getId(), testUser);
    assertNull(subContainer.getParentContainer());
    listContainer = containerApiMgr.getApiContainerById(listContainer.getId(), testUser);
    expectedAccessedEventsCount++;
    assertEquals(0, listContainer.getContentSummary().getTotalCount());
    Mockito.verify(mockPublisher, Mockito.times(1))
        .publishEvent(Mockito.any(InventoryDeleteEvent.class));
    Mockito.verify(mockPublisher, Mockito.times(expectedAccessedEventsCount))
        .publishEvent(Mockito.any(InventoryAccessEvent.class));

    Mockito.verifyNoMoreInteractions(mockPublisher);
  }

  @Test
  public void moveValidationErrors() throws Exception {

    ApiContainer workbench = getWorkbenchForUser(testUser);

    // create container with 2 subcontainers
    ApiContainer topLevelContainer =
        containerApiMgr.createNewApiContainer(new ApiContainer(), testUser);
    ApiContainer newSubContainer = new ApiContainer();
    newSubContainer.setParentContainer(topLevelContainer);
    ApiContainer subContainer = containerApiMgr.createNewApiContainer(newSubContainer, testUser);
    ApiContainer subContainer2 = containerApiMgr.createNewApiContainer(newSubContainer, testUser);
    assertEquals(topLevelContainer.getId(), subContainer.getParentContainer().getId());
    assertEquals(topLevelContainer.getId(), subContainer2.getParentContainer().getId());
    Mockito.verify(mockPublisher, Mockito.times(3))
        .publishEvent(Mockito.any(InventoryCreationEvent.class));
    Mockito.verify(mockPublisher, Mockito.times(2))
        .publishEvent(Mockito.any(InventoryMoveEvent.class));

    // assert move action not triggered when setting the same parent
    ApiContainer updateRequest = new ApiContainer();
    updateRequest.setId(subContainer.getId());
    updateRequest.setParentContainer(topLevelContainer);
    containerApiMgr.updateApiContainer(updateRequest, testUser);
    Mockito.verify(mockPublisher, Mockito.never())
        .publishEvent(Mockito.any(InventoryEditingEvent.class));

    // moving to itself is rejected
    updateRequest.setParentContainer(subContainer);
    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> containerApiMgr.updateApiContainer(updateRequest, testUser));
    assertEquals("Cannot move container into itself", iae.getMessage());

    // moving parent to its child is rejected
    updateRequest.setId(topLevelContainer.getId());
    updateRequest.setParentContainer(subContainer);
    iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> containerApiMgr.updateApiContainer(updateRequest, testUser));
    assertEquals("Cannot move container into its subcontainer", iae.getMessage());

    // cannot move into deleted container
    ApiContainer deletedContainer =
        containerApiMgr.createNewApiContainer(newSubContainer, testUser);
    containerApiMgr.markContainerAsDeleted(deletedContainer.getId(), testUser);
    Mockito.verify(mockPublisher, Mockito.times(4))
        .publishEvent(Mockito.any(InventoryCreationEvent.class));
    Mockito.verify(mockPublisher, Mockito.times(3))
        .publishEvent(Mockito.any(InventoryMoveEvent.class));
    Mockito.verify(mockPublisher, Mockito.times(1))
        .publishEvent(Mockito.any(InventoryDeleteEvent.class));

    updateRequest.setId(subContainer.getId());
    updateRequest.setParentContainer(deletedContainer);
    iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> containerApiMgr.updateApiContainer(updateRequest, testUser));
    assertEquals("Cannot move into deleted container", iae.getMessage());

    // cannot move into other user's container
    User otherUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(otherUser);
    ApiContainer otherUserWorkbench = getWorkbenchForUser(otherUser);

    updateRequest.setId(topLevelContainer.getId());
    updateRequest.setParentContainer(otherUserWorkbench);
    ApiRuntimeException are =
        assertThrows(
            ApiRuntimeException.class,
            () -> containerApiMgr.updateApiContainer(updateRequest, testUser));
    assertEquals("move.failure.cannot.locate.target.container", are.getMessage());

    // sanity check - can move to another subcontainer
    updateRequest.setId(subContainer.getId());
    updateRequest.setParentContainer(subContainer2);
    containerApiMgr.updateApiContainer(updateRequest, testUser);
    Mockito.verify(mockPublisher, Mockito.times(4))
        .publishEvent(Mockito.any(InventoryMoveEvent.class));

    subContainer = containerApiMgr.getApiContainerById(subContainer.getId(), testUser);
    assertEquals(3, subContainer.getParentContainers().size());
    assertEquals(subContainer2.getId(), subContainer.getParentContainers().get(0).getId());
    assertEquals(topLevelContainer.getId(), subContainer.getParentContainers().get(1).getId());
    assertEquals(workbench.getId(), subContainer.getParentContainers().get(2).getId());
    Mockito.verify(mockPublisher, Mockito.times(1))
        .publishEvent(Mockito.any(InventoryAccessEvent.class));

    Mockito.verifyNoMoreInteractions(mockPublisher);
  }

  @Test
  public void deleteContainer() {

    ISearchResults<ApiContainerInfo> userContainersResult =
        containerApiMgr.getTopContainersForUser(
            PaginationCriteria.createDefaultForClass(Container.class), null, null, testUser);
    final int intitalCount = userContainersResult.getHits().intValue();
    ApiContainer testContainer = createBasicContainerForUser(testUser);
    moveContainerToTopLevel(testContainer, testUser);
    assertNotNull(testContainer);
    assertFalse(testContainer.isDeleted());
    Mockito.verify(mockPublisher).publishEvent(Mockito.any(InventoryCreationEvent.class));

    // add a subcontainer
    ApiContainer subContainer = createBasicContainerForUser(testUser);
    Mockito.verify(mockPublisher, Mockito.times(2))
        .publishEvent(Mockito.any(InventoryCreationEvent.class));
    ApiContainer updatedSubContainer =
        moveContainerIntoListContainer(subContainer.getId(), testContainer.getId(), testUser);
    assertEquals(testContainer.getId(), updatedSubContainer.getParentContainer().getId());
    Mockito.verify(mockPublisher, Mockito.times(2))
        .publishEvent(Mockito.any(InventoryMoveEvent.class));

    // check containers listing
    ISearchResults<ApiContainerInfo> defaultContainerResult =
        containerApiMgr.getTopContainersForUser(
            PaginationCriteria.createDefaultForClass(Container.class), null, null, testUser);
    assertEquals(intitalCount + 1, defaultContainerResult.getTotalHits());

    // try deleting main container
    ApiRuntimeException are =
        assertThrows(
            ApiRuntimeException.class,
            () -> containerApiMgr.markContainerAsDeleted(testContainer.getId(), testUser));
    assertEquals("container.deletion.failure.not.empty", are.getMessage());
    assertEquals(testContainer.getGlobalId(), are.getArgs()[0]);
    Mockito.verify(mockPublisher, Mockito.never())
        .publishEvent(Mockito.any(InventoryDeleteEvent.class));

    // delete subcontainer, than main container
    containerApiMgr.markContainerAsDeleted(subContainer.getId(), testUser);
    containerApiMgr.markContainerAsDeleted(testContainer.getId(), testUser);
    Mockito.verify(mockPublisher, Mockito.times(2))
        .publishEvent(Mockito.any(InventoryDeleteEvent.class));

    // check deleted container can be accessed
    ApiContainer deletedContainer =
        containerApiMgr.getApiContainerById(testContainer.getId(), testUser);
    assertTrue(deletedContainer.isDeleted());
    Mockito.verify(mockPublisher).publishEvent(Mockito.any(InventoryAccessEvent.class));

    // but no longer present in default listing
    defaultContainerResult =
        containerApiMgr.getTopContainersForUser(
            PaginationCriteria.createDefaultForClass(Container.class), null, null, testUser);
    assertEquals(intitalCount, defaultContainerResult.getTotalHits());

    // still both deleted containers are seen with 'include deleted' option
    defaultContainerResult =
        containerApiMgr.getTopContainersForUser(
            PaginationCriteria.createDefaultForClass(Container.class),
            null,
            InventorySearchDeletedOption.INCLUDE,
            testUser);
    assertEquals(intitalCount + 2, defaultContainerResult.getTotalHits());

    // restore the main container
    ApiContainer restoredContainer =
        containerApiMgr.restoreDeletedContainer(testContainer.getId(), testUser);
    assertFalse(restoredContainer.isDeleted());
    // restored container moved to workbench
    assertTrue(restoredContainer.getParentContainer().isWorkbench());
    Mockito.verify(mockPublisher).publishEvent(Mockito.any(InventoryRestoreEvent.class));

    Mockito.verifyNoMoreInteractions(mockPublisher);
  }

  private void verifyCreationEventPublished() {
    Mockito.verify(mockPublisher).publishEvent(Mockito.any(InventoryCreationEvent.class));
  }

  @Test
  public void duplicateContainer() throws Exception {
    ApiContainer newContainer = createBasicGridContainerForUser(testUser, 17, 7);
    ApiContainer duplicate = containerApiMgr.duplicate(newContainer.getId(), testUser);
    // all extra fields are created ok
    assertEquals(17, duplicate.getGridLayout().getColumnsNumber());
    assertFalse(duplicate.getId().equals(newContainer.getId()));
    assertTrue(duplicate.getParentContainer().isWorkbench());

    // workbench cannot be duplicated
    ApiContainer workbench = getWorkbenchForUser(testUser);
    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> containerApiMgr.duplicate(workbench.getId(), testUser));
    assertTrue(iae.getMessage().endsWith("is a workbench"));
  }

  @Test
  public void transferContainerToAnotherUser() {
    ApiContainer testUserWorkbench = getWorkbenchForUser(testUser);

    // create test container
    ApiContainer testContainer = createBasicContainerForUser(testUser);
    assertEquals(testUser.getUsername(), testContainer.getOwner().getUsername());
    assertEquals(testUserWorkbench.getId(), testContainer.getParentContainer().getId());
    Mockito.verify(mockPublisher, Mockito.times(1))
        .publishEvent(Mockito.any(InventoryCreationEvent.class));

    containerApiMgr.getApiContainerById(testContainer.getId(), testUser);
    assertEquals(testUser.getUsername(), testContainer.getModifiedBy());
    Long initialLastModifiedMillis = testContainer.getLastModifiedMillis();
    Mockito.verify(mockPublisher, Mockito.times(1))
        .publishEvent(Mockito.any(InventoryAccessEvent.class));

    // create another user
    User otherUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(otherUser);
    ApiContainer otherUserWorkbench = getWorkbenchForUser(otherUser);

    // create change owner request
    ApiContainer containerUpdate = new ApiContainer();
    containerUpdate.setId(testContainer.getId());
    containerUpdate.setOwner(new ApiUser());

    // check error message for unexisting user
    containerUpdate.getOwner().setUsername("incorrectUname");
    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> containerApiMgr.changeApiContainerOwner(containerUpdate, testUser));
    assertEquals("Target user [incorrectUname] not found", iae.getMessage());

    // run change owner request as test user
    containerUpdate.getOwner().setUsername(otherUser.getUsername());
    ApiContainer updatedContainer =
        containerApiMgr.changeApiContainerOwner(containerUpdate, testUser);

    // verify that owner updated
    assertEquals(otherUser.getUsername(), updatedContainer.getOwner().getUsername());
    // no other details available - user no longer has access to the sample!
    assertTrue(updatedContainer.isClearedForPublicView());
    Mockito.verify(mockPublisher).publishEvent(Mockito.any(InventoryTransferEvent.class));

    updatedContainer = containerApiMgr.getApiContainerById(testContainer.getId(), otherUser);
    // new owner can read
    assertFalse(updatedContainer.isClearedForPublicView());
    // location changed to new user's workbench
    assertEquals(otherUserWorkbench.getId(), updatedContainer.getParentContainer().getId());
    // last modification details not updated (ownership change doesn't count as a modification)
    assertEquals(testUser.getUsername(), testContainer.getModifiedBy());
    assertEquals(initialLastModifiedMillis, updatedContainer.getLastModifiedMillis());
    Mockito.verify(mockPublisher, Mockito.times(2))
        .publishEvent(Mockito.any(InventoryAccessEvent.class));

    Mockito.verifyNoMoreInteractions(mockPublisher);
  }

  @Test
  public void containerEditedByTwoUsers() {

    // create a pi, with a group
    User piUser = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    initialiseContentWithEmptyContent(piUser);
    Group group = createGroup("group", piUser);
    addUsersToGroup(piUser, group, testUser);

    // create a container
    ApiContainer testContainer = createBasicContainerForUser(testUser);

    // lock container by pi
    ApiInventoryEditLock apiLock =
        invLockTracker.attemptToLockForEdit(testContainer.getGlobalId(), piUser);
    assertEquals(ApiInventoryEditLockStatus.LOCKED_OK, apiLock.getStatus());

    // try edit by testUser
    testContainer.setName("updated name");
    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> containerApiMgr.updateApiContainer(testContainer, testUser));
    assertTrue(iae.getMessage().startsWith("Item is currently edited by another user ("));

    // try delete by testUser
    iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> containerApiMgr.markContainerAsDeleted(testContainer.getId(), testUser));
    assertTrue(iae.getMessage().startsWith("Item is currently edited by another user ("));

    // try transfer by testUser
    iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> containerApiMgr.changeApiContainerOwner(testContainer, testUser));
    assertTrue(iae.getMessage().startsWith("Item is currently edited by another user ("));

    // pi can edit fine
    ApiContainer updatedContainer = containerApiMgr.updateApiContainer(testContainer, piUser);
    assertEquals("updated name", updatedContainer.getName());

    // pi unlocks
    invLockTracker.attemptToUnlock(testContainer.getGlobalId(), piUser);

    // testUser can now edit fine
    testContainer.setName("updated name 2");
    updatedContainer = containerApiMgr.updateApiContainer(testContainer, testUser);
    assertEquals("updated name 2", updatedContainer.getName());
  }

  @Test
  public void workbenchVisibilityWithinGroup() {

    // create new users, and containers
    User pi = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    initialiseContentWithEmptyContent(pi);
    createBasicContainerForUser(pi, "pi's cont");

    // initially pi can see only their workbench
    ISearchResults<ApiContainerInfo> workbenchResult =
        containerApiMgr.getWorkbenchesForUser(
            PaginationCriteria.createDefaultForClass(Container.class), null, pi);

    // check visibility within a group
    Group group = createGroup("group", pi);
    addUsersToGroup(pi, group, testUser);

    // pi can see both workbenches
    workbenchResult =
        containerApiMgr.getWorkbenchesForUser(
            PaginationCriteria.createDefaultForClass(Container.class), null, pi);
    assertEquals(2, workbenchResult.getTotalHits());
    assertEquals("WORKBENCH", workbenchResult.getFirstResult().getCType());

    // user can also see both workbenches
    workbenchResult =
        containerApiMgr.getWorkbenchesForUser(
            PaginationCriteria.createDefaultForClass(Container.class), null, testUser);
    assertEquals(2, workbenchResult.getTotalHits());
    assertEquals("WORKBENCH", workbenchResult.getFirstResult().getCType());
  }

  @Test
  public void moveContainerToWorkbench() throws InterruptedException {

    ApiContainer workbench = getWorkbenchForUser(testUser);
    assertTrue(workbench.isWorkbench());

    // container and subcontainer to move around
    ApiContainer listContainer = createBasicContainerForUser(testUser);
    ApiContainer subContainer = createBasicContainerForUser(testUser, "test subcontainer");

    // starts at workbench
    assertEquals(workbench.getId(), subContainer.getParentContainer().getId());
    assertNull(subContainer.getLastNonWorkbenchParent());
    assertNull(subContainer.getLastMoveDateMillis());

    // move to be top-level container
    ApiContainer updateRequest = new ApiContainer();
    updateRequest.setId(subContainer.getId());
    updateRequest.setRemoveFromParentContainerRequest(true);
    ApiContainer updatedSubContainer = containerApiMgr.updateApiContainer(updateRequest, testUser);
    assertNull(updatedSubContainer.getParentContainer());
    assertNull(updatedSubContainer.getLastNonWorkbenchParent());
    Long topLevelMoveDateMillis = updatedSubContainer.getLastMoveDateMillis();
    assertNotNull(topLevelMoveDateMillis);

    // move inside list container
    Thread.sleep(100);
    updateRequest = new ApiContainer();
    updateRequest.setId(subContainer.getId());
    updateRequest.setParentContainer(listContainer);
    updatedSubContainer = containerApiMgr.updateApiContainer(updateRequest, testUser);
    assertEquals(listContainer.getId(), updatedSubContainer.getParentContainer().getId());
    assertNull(updatedSubContainer.getLastNonWorkbenchParent());
    Long listMoveDateMillis = updatedSubContainer.getLastMoveDateMillis();
    assertTrue(listMoveDateMillis > topLevelMoveDateMillis);

    // move back to workbench
    Thread.sleep(100);
    updateRequest = new ApiContainer();
    updateRequest.setId(subContainer.getId());
    updateRequest.setParentContainer(workbench);
    updatedSubContainer = containerApiMgr.updateApiContainer(updateRequest, testUser);
    assertEquals(workbench.getId(), updatedSubContainer.getParentContainer().getId());
    assertEquals(listContainer.getId(), updatedSubContainer.getLastNonWorkbenchParent().getId());
    Long workbenchMoveDateMillis = updatedSubContainer.getLastMoveDateMillis();
    assertNotNull(workbenchMoveDateMillis);
    assertTrue(workbenchMoveDateMillis > listMoveDateMillis);

    // move back to list container
    Thread.sleep(100);
    updateRequest = new ApiContainer();
    updateRequest.setId(subContainer.getId());
    updateRequest.setParentContainer(listContainer);
    updatedSubContainer = containerApiMgr.updateApiContainer(updateRequest, testUser);
    assertEquals(listContainer.getId(), updatedSubContainer.getParentContainer().getId());
    assertEquals(listContainer.getId(), updatedSubContainer.getLastNonWorkbenchParent().getId());
    Long anotherListMoveDateMillis = updatedSubContainer.getLastMoveDateMillis();
    assertNotNull(anotherListMoveDateMillis);
    assertTrue(anotherListMoveDateMillis > workbenchMoveDateMillis);
  }

  @Test
  public void checkWorkbenchRetrieval() {
    ApiContainer fullWorkbench = getWorkbenchForUser(testUser);
    assertNotNull(fullWorkbench);
    assertNotNull(fullWorkbench.getId());
    assertEquals(1, fullWorkbench.getContentSummary().getTotalCount());
    Mockito.verify(mockPublisher, Mockito.times(0))
        .publishEvent(Mockito.any(InventoryAccessEvent.class));

    Long workbenchId = containerApiMgr.getWorkbenchIdForUser(testUser);
    assertEquals(fullWorkbench.getId(), workbenchId);

    // confirm that for uninitialized user retrieval of workbench id triggers workbench creation
    User pi = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"), Constants.PI_ROLE);
    Long piWorkbenchId = containerApiMgr.getWorkbenchIdForUser(pi);
    assertNotNull(piWorkbenchId);

    // create group with pi and testUser
    initialiseContentWithEmptyContent(pi);
    Group group = createGroup("group", pi);
    addUsersToGroup(pi, group, testUser);

    // confirm that retrieving own workbench doesn't trigger audit event (RSINV-708)
    containerApiMgr.getApiWorkbenchById(workbenchId, testUser);
    containerApiMgr.getApiWorkbenchById(piWorkbenchId, pi);
    Mockito.verify(mockPublisher, Mockito.times(0))
        .publishEvent(Mockito.any(InventoryAccessEvent.class));

    // confirm that retrieving other user's workbench does trigger audit event
    containerApiMgr.getApiWorkbenchById(workbenchId, pi);
    containerApiMgr.getApiWorkbenchById(piWorkbenchId, testUser);
    Mockito.verify(mockPublisher, Mockito.times(2))
        .publishEvent(Mockito.any(InventoryAccessEvent.class));

    Mockito.verifyNoMoreInteractions(mockPublisher);
  }

  @Test
  public void containerSharingPermissions() {

    // create a pi and other user
    User piUser = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    initialiseContentWithEmptyContent(piUser);
    User otherUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(otherUser);

    // groupA with pi and test user
    Group groupA = createGroup("groupA", piUser);
    addUsersToGroup(piUser, groupA, testUser);
    // groupB with pi and other user
    Group groupB = createGroup("groupB", piUser);
    addUsersToGroup(piUser, groupB, otherUser);

    // create a container, check default sharing
    ApiContainer createdContainer = createBasicContainerForUser(testUser);
    assertEquals(ApiInventorySharingMode.OWNER_GROUPS, createdContainer.getSharingMode());
    assertNotNull(createdContainer.getSharedWith());
    assertEquals(1, createdContainer.getSharedWith().size());
    assertEquals("groupA", createdContainer.getSharedWith().get(0).getGroupInfo().getName());
    assertFalse(createdContainer.getSharedWith().get(0).isShared());

    // save with whitelist permissions pointing to groupB
    ApiContainer containerUpdates = new ApiContainer();
    containerUpdates.setId(createdContainer.getId());
    containerUpdates.setSharingMode(ApiInventorySharingMode.WHITELIST);
    containerUpdates.setSharedWith(
        List.of(ApiGroupInfoWithSharedFlag.forSharingWithGroup(groupB, testUser)));
    ApiContainer updatedContainer = containerApiMgr.updateApiContainer(containerUpdates, testUser);
    assertNotNull(updatedContainer);
    assertEquals(ApiInventorySharingMode.WHITELIST, updatedContainer.getSharingMode());
    assertNotNull(updatedContainer.getSharedWith());
    assertEquals(2, updatedContainer.getSharedWith().size());
    assertEquals("groupA", updatedContainer.getSharedWith().get(0).getGroupInfo().getName());
    assertFalse(
        updatedContainer.getSharedWith().get(0).isShared()); // owner's groups are always present
    assertEquals("groupB", updatedContainer.getSharedWith().get(1).getGroupInfo().getName());
    assertTrue(updatedContainer.getSharedWith().get(1).isShared());
  }

  @Test
  public void checkLimitedReadContainerActions() {

    // create a pi and other user
    User piUser = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    User otherUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(piUser, otherUser);

    // groupA with pi and test user
    Group groupA = createGroup("groupA", piUser);
    addUsersToGroup(piUser, groupA, testUser);
    // groupB with pi and other user
    Group groupB = createGroup("groupB", piUser);
    addUsersToGroup(piUser, groupB, otherUser);

    // create a container shared with groupB, and a subcontainer (with subsubcontainer) in it
    ApiContainer apiContainer = createBasicContainerForUser(testUser, "c1", List.of(groupB));
    ApiContainer apiSubContainer = createBasicContainerForUser(testUser, "c2");
    moveContainerIntoListContainer(apiSubContainer.getId(), apiContainer.getId(), testUser);

    // as other user try retrieving the shared container
    ApiContainer containerAsSeenByOtherUser =
        containerApiMgr.getApiContainerById(apiContainer.getId(), otherUser);
    assertEquals(2, containerAsSeenByOtherUser.getPermittedActions().size());
    assertEquals(
        ApiInventoryRecordInfo.ApiInventoryRecordPermittedAction.READ,
        containerAsSeenByOtherUser.getPermittedActions().get(0));
    assertEquals(
        ApiInventoryRecordInfo.ApiInventoryRecordPermittedAction.UPDATE,
        containerAsSeenByOtherUser.getPermittedActions().get(1));
    ApiContainerInfo subContainerInfoAsSeenByOtherUser =
        (ApiContainerInfo) containerAsSeenByOtherUser.getStoredContent().get(0);
    assertEquals(1, subContainerInfoAsSeenByOtherUser.getPermittedActions().size());
    assertEquals(
        ApiInventoryRecordInfo.ApiInventoryRecordPermittedAction.LIMITED_READ,
        subContainerInfoAsSeenByOtherUser.getPermittedActions().get(0));
    assertNotNull(subContainerInfoAsSeenByOtherUser.getName());
    assertNotNull(subContainerInfoAsSeenByOtherUser.getBarcodes());
    assertNotNull(subContainerInfoAsSeenByOtherUser.getParentContainers());
    assertNotNull(subContainerInfoAsSeenByOtherUser.getCType());
    assertNotNull(subContainerInfoAsSeenByOtherUser.getContentSummary());
    assertNull(subContainerInfoAsSeenByOtherUser.getModifiedBy()); // unavailable in limited view

    // as otherUser try retrieving the subcontainer
    ApiContainer subContainerAsSeenByOtherUser =
        containerApiMgr.getApiContainerById(apiSubContainer.getId(), otherUser);
    assertEquals(1, subContainerAsSeenByOtherUser.getPermittedActions().size());
    assertEquals(
        ApiInventoryRecordInfo.ApiInventoryRecordPermittedAction.LIMITED_READ,
        subContainerAsSeenByOtherUser.getPermittedActions().get(0));
    // assert only some fields populated
    assertNotNull(subContainerAsSeenByOtherUser.getName());
    assertNotNull(subContainerAsSeenByOtherUser.getBarcodes());
    assertNotNull(subContainerAsSeenByOtherUser.getParentContainers());
    assertNotNull(subContainerAsSeenByOtherUser.getCType());
    assertNotNull(subContainerAsSeenByOtherUser.getContentSummary());
    assertNull(subContainerAsSeenByOtherUser.getModifiedBy());
    assertNull(subContainerAsSeenByOtherUser.getExtraFields());
    assertNull(subContainerAsSeenByOtherUser.getLocations());

    // compare with subcontainer as seen by the owner
    ApiContainer subContainerAsSeenByTestUser =
        containerApiMgr.getApiContainerById(apiSubContainer.getId(), testUser);
    assertEquals(3, subContainerAsSeenByTestUser.getPermittedActions().size());
    assertNotNull(subContainerAsSeenByTestUser.getModifiedBy());
    assertNotNull(subContainerAsSeenByTestUser.getExtraFields());
    assertNotNull(subContainerAsSeenByTestUser.getLocations());
  }

  @Test
  public void readSharedContainerAfterGroupDeletion_RSINV761() {

    // create a pi and other user
    User piUser = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    User otherUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(piUser, otherUser);

    // groupA with pi and other user
    Group groupA = createGroup("groupA", piUser);
    addUsersToGroup(piUser, groupA, otherUser);

    // create a container shared with groupA, and a subcontainer (with subsubcontainer) in it
    ApiContainer apiContainer = createBasicContainerForUser(testUser, "c1", List.of(groupA));
    ApiContainer containerAsSeenByTestUser =
        containerApiMgr.getApiContainerById(apiContainer.getId(), testUser);
    assertEquals(3, containerAsSeenByTestUser.getPermittedActions().size());
    ApiContainer containerAsSeenByOtherUser =
        containerApiMgr.getApiContainerById(apiContainer.getId(), otherUser);
    assertEquals(2, containerAsSeenByOtherUser.getPermittedActions().size());

    // now delete the group
    User sysadmin = logoutAndLoginAsSysAdmin();
    grpMgr.removeGroup(groupA.getId(), sysadmin);

    // container can no longer be read by other user
    containerAsSeenByOtherUser =
        containerApiMgr.getApiContainerById(apiContainer.getId(), otherUser);
    assertTrue(containerAsSeenByOtherUser.isPublicReadItem());

    // can be read by test user fine
    containerAsSeenByTestUser = containerApiMgr.getApiContainerById(apiContainer.getId(), testUser);
    assertEquals(3, containerAsSeenByTestUser.getPermittedActions().size());
  }
}
