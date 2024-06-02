package com.researchspace.service.inventory;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.Constants;
import com.researchspace.api.v1.model.ApiBasket;
import com.researchspace.api.v1.model.ApiBasketInfo;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordPermittedAction;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.junit.Test;
import org.springframework.validation.BindException;

public class BasketApiManagerTest extends SpringTransactionalTest {

  @Test
  public void testBasicBasketOperations() throws BindException {

    User user = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(user);

    ApiSampleWithFullSubSamples basicSample = createBasicSampleForUser(user);
    ApiSubSample basicSubSample = basicSample.getSubSamples().get(0);

    List<ApiBasketInfo> userBaskets = basketApiMgr.getBasketsForUser(user);
    assertEquals(0, userBaskets.size());

    // create the basket
    ApiBasket createdBasket =
        basketApiMgr.createNewBasket("test basket", List.of(basicSubSample.getGlobalId()), user);
    assertNotNull(createdBasket.getId());
    assertEquals("test basket", createdBasket.getName());
    assertEquals(1, createdBasket.getItemCount());

    // rename the basket
    ApiBasket basketUpdate = new ApiBasket();
    basketUpdate.setId(createdBasket.getId());
    basketUpdate.setName("test basket renamed");
    basketApiMgr.updateApiBasket(basketUpdate, user);

    // check created & renamed
    userBaskets = basketApiMgr.getBasketsForUser(user);
    assertEquals(1, userBaskets.size());
    assertEquals("test basket renamed", userBaskets.get(0).getName());

    // add an item (items already in basket are skipped)
    ApiBasket updatedBasket =
        basketApiMgr.addItemsToBasket(
            createdBasket.getId(),
            List.of(basicSample.getGlobalId(), basicSubSample.getGlobalId()),
            user);
    assertEquals(2, updatedBasket.getItemCount());
    assertEquals(basicSubSample.getGlobalId(), updatedBasket.getItems().get(0).getGlobalId());
    assertEquals(basicSample.getGlobalId(), updatedBasket.getItems().get(1).getGlobalId());

    // remove one of the items
    updatedBasket =
        basketApiMgr.removeItemsFromBasket(
            createdBasket.getId(), List.of(basicSubSample.getGlobalId()), user);
    assertEquals(1, updatedBasket.getItemCount());
    assertEquals(basicSample.getGlobalId(), updatedBasket.getItems().get(0).getGlobalId());

    // delete the basket
    basketApiMgr.deleteBasketById(createdBasket.getId(), user);
    userBaskets = basketApiMgr.getBasketsForUser(user);
    assertEquals(0, userBaskets.size());
  }

  @Test
  public void checkLimitedReadBasketActions() {

    // create a test user who will own and share items
    User testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(testUser);

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

    // create a container shared with groupB, a group-owned subcontainer in that container, and
    // group-owned sample with subsample in that container
    ApiContainer apiContainer = createBasicContainerForUser(testUser, "c1", List.of(groupB));
    ApiContainer apiSubContainer = createBasicContainerForUser(testUser, "c2");
    moveContainerIntoListContainer(apiSubContainer.getId(), apiContainer.getId(), testUser);
    ApiSampleWithFullSubSamples apiSample = createComplexSampleForUser(testUser);
    ApiSubSample apiSubSample = apiSample.getSubSamples().get(0);
    moveSubSampleIntoListContainer(apiSubSample.getId(), apiContainer.getId(), testUser);

    // other user should have full access to container and limited access to
    // subcontainer/subsample/sample
    // testUser should now have limited view permission to the items
    assertTrue(
        invPermissionUtils.canUserReadOrLimitedReadInventoryRecord(
            apiContainer.getOid(), otherUser));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(apiContainer.getOid(), otherUser));
    assertTrue(
        invPermissionUtils.canUserReadOrLimitedReadInventoryRecord(
            apiSubContainer.getOid(), otherUser));
    assertFalse(invPermissionUtils.canUserReadInventoryRecord(apiSubContainer.getOid(), otherUser));
    assertTrue(
        invPermissionUtils.canUserReadOrLimitedReadInventoryRecord(apiSample.getOid(), otherUser));
    assertFalse(invPermissionUtils.canUserReadInventoryRecord(apiSample.getOid(), otherUser));
    assertTrue(
        invPermissionUtils.canUserReadOrLimitedReadInventoryRecord(
            apiSubSample.getOid(), otherUser));
    assertFalse(invPermissionUtils.canUserReadInventoryRecord(apiSubSample.getOid(), otherUser));

    // other user should be able to create a basket with all the items
    ApiBasket createdBasket =
        basketApiMgr.createNewBasket(
            "test basket",
            List.of(
                apiContainer.getGlobalId(),
                apiSubContainer.getGlobalId(),
                apiSample.getGlobalId(),
                apiSubSample.getGlobalId()),
            otherUser);
    assertNotNull(createdBasket.getId());
    assertEquals("test basket", createdBasket.getName());
    assertEquals(4, createdBasket.getItemCount());
    // basket listing should show items in full/limited-view mode
    List<ApiInventoryRecordInfo> itemsFromCreatedBasket = createdBasket.getItems();
    ApiInventoryRecordInfo apiContainerFromBasket = itemsFromCreatedBasket.get(0);
    assertEquals(apiContainer.getGlobalId(), apiContainerFromBasket.getGlobalId());
    assertEquals(
        List.of(ApiInventoryRecordPermittedAction.READ, ApiInventoryRecordPermittedAction.UPDATE),
        apiContainerFromBasket.getPermittedActions());
    assertNotNull(apiContainerFromBasket.getCreatedBy()); // available in full view
    ApiInventoryRecordInfo apiSubContainerFromBasket = itemsFromCreatedBasket.get(1);
    assertEquals(apiSubContainer.getGlobalId(), apiSubContainerFromBasket.getGlobalId());
    assertEquals(
        List.of(ApiInventoryRecordPermittedAction.LIMITED_READ),
        apiSubContainerFromBasket.getPermittedActions());
    assertNull(apiSubContainerFromBasket.getCreatedBy()); // not available in limited view
    ApiInventoryRecordInfo apiSampleFromBasket = itemsFromCreatedBasket.get(2);
    assertEquals(apiSample.getGlobalId(), apiSampleFromBasket.getGlobalId());
    assertEquals(
        List.of(ApiInventoryRecordPermittedAction.LIMITED_READ),
        apiSampleFromBasket.getPermittedActions());
    assertNull(apiSampleFromBasket.getCreatedBy());
    ApiInventoryRecordInfo apiSubSampleFromBasket = itemsFromCreatedBasket.get(3);
    assertEquals(apiSubSample.getGlobalId(), apiSubSampleFromBasket.getGlobalId());
    assertEquals(
        List.of(ApiInventoryRecordPermittedAction.LIMITED_READ),
        apiSubSampleFromBasket.getPermittedActions());
    assertNull(apiSubSampleFromBasket.getCreatedBy());
  }

  @Test
  public void checkPublicViewBasketActions() {

    // create a test user who will own and share items
    User testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    User otherUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(testUser, otherUser);

    // create a container shared with groupB, a group-owned subcontainer in that container, and
    // group-owned sample with subsample in that container
    ApiContainer apiContainer = createBasicContainerForUser(testUser, "c1", null);
    ApiSampleWithFullSubSamples apiSample = createComplexSampleForUser(testUser);
    ApiSubSample apiSubSample = apiSample.getSubSamples().get(0);

    // other user should have no access to items
    assertFalse(
        invPermissionUtils.canUserReadOrLimitedReadInventoryRecord(
            apiContainer.getOid(), otherUser));
    assertFalse(
        invPermissionUtils.canUserReadOrLimitedReadInventoryRecord(apiSample.getOid(), otherUser));
    assertFalse(
        invPermissionUtils.canUserReadOrLimitedReadInventoryRecord(
            apiSubSample.getOid(), otherUser));

    // other user should be able to create a basket with all the items
    ApiBasket createdBasket =
        basketApiMgr.createNewBasket(
            "test basket",
            List.of(
                apiContainer.getGlobalId(), apiSample.getGlobalId(), apiSubSample.getGlobalId()),
            otherUser);
    assertNotNull(createdBasket.getId());
    assertEquals("test basket", createdBasket.getName());
    assertEquals(3, createdBasket.getItemCount());
    // basket listing should show items in public-view mode
    List<ApiInventoryRecordInfo> itemsFromCreatedBasket = createdBasket.getItems();
    ApiInventoryRecordInfo apiContainerFromBasket = itemsFromCreatedBasket.get(0);
    assertEquals(apiContainer.getGlobalId(), apiContainerFromBasket.getGlobalId());
    assertTrue(apiContainerFromBasket.isClearedForPublicView());

    // should be able to remove items too
    ApiBasket updatedBasket =
        basketApiMgr.removeItemsFromBasket(
            createdBasket.getId(), List.of(apiContainer.getGlobalId()), otherUser);
    assertEquals(2, updatedBasket.getItemCount());
  }
}
