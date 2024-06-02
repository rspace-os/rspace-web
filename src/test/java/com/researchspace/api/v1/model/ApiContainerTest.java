package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.researchspace.model.User;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Test;

public class ApiContainerTest extends SpringTransactionalTest {

  @Test
  public void checkReducingContainerToLimitedView() {

    User user = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithExampleContent(user);
    ApiContainer apiContainer = createBasicContainerForUser(user);
    apiContainer = containerApiMgr.getApiContainerById(apiContainer.getId(), user);

    // create artificial container that only has properties expected in limited view, as defined in
    // RSINV-705
    ApiContainer containerWithJustLimitedViewProperties = new ApiContainer();
    // ApiInventoryRecordInfo level properties
    containerWithJustLimitedViewProperties.setId(apiContainer.getId());
    containerWithJustLimitedViewProperties.setGlobalId(apiContainer.getGlobalId());
    containerWithJustLimitedViewProperties.setName(apiContainer.getName());
    containerWithJustLimitedViewProperties.setType(apiContainer.getType());
    containerWithJustLimitedViewProperties.setOwner(apiContainer.getOwner());
    containerWithJustLimitedViewProperties.setBarcodes(apiContainer.getBarcodes());
    containerWithJustLimitedViewProperties.setCustomImage(apiContainer.isCustomImage());
    containerWithJustLimitedViewProperties.setIconId(apiContainer.getIconId());
    containerWithJustLimitedViewProperties.setTags(apiContainer.getTags());
    containerWithJustLimitedViewProperties.setDescription(apiContainer.getDescription());
    containerWithJustLimitedViewProperties.setPermittedActions(apiContainer.getPermittedActions());
    containerWithJustLimitedViewProperties.setAttachments(null);
    containerWithJustLimitedViewProperties.setLinks(apiContainer.getLinks());
    // ApiContainerInfo-level properties
    containerWithJustLimitedViewProperties.setContentSummary(apiContainer.getContentSummary());
    containerWithJustLimitedViewProperties.setParentContainers(apiContainer.getParentContainers());
    containerWithJustLimitedViewProperties.setParentLocation(apiContainer.getParentLocation());
    containerWithJustLimitedViewProperties.setCType(apiContainer.getCType());
    containerWithJustLimitedViewProperties.setCanStoreContainers(
        apiContainer.getCanStoreContainers());
    containerWithJustLimitedViewProperties.setCanStoreSamples(apiContainer.getCanStoreSamples());
    // ApiContainer level properties
    containerWithJustLimitedViewProperties.setSharedWith(null);
    containerWithJustLimitedViewProperties.setExtraFields(null);
    containerWithJustLimitedViewProperties.setLocations(null);

    // retrieved full-view container  will contain more properties than created one
    assertNotEquals(containerWithJustLimitedViewProperties, apiContainer);

    // limited-view copy should be equal to one having just the known field populated
    apiContainer.clearPropertiesForLimitedView();
    assertEquals(containerWithJustLimitedViewProperties, apiContainer);
  }

  @Test
  public void checkReducingContainerToPublicView() {
    User user = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithExampleContent(user);
    ApiContainer apiContainer = createBasicContainerForUser(user);
    apiContainer = containerApiMgr.getApiContainerById(apiContainer.getId(), user);

    // create artificial container that only has properties expected in global view, as defined in
    // RSINV-212
    ApiContainer containerWithJustGlobalViewProperties = new ApiContainer();
    containerWithJustGlobalViewProperties.setId(apiContainer.getId());
    containerWithJustGlobalViewProperties.setGlobalId(apiContainer.getGlobalId());
    containerWithJustGlobalViewProperties.setType(apiContainer.getType());
    containerWithJustGlobalViewProperties.setName(apiContainer.getName());
    containerWithJustGlobalViewProperties.setOwner(apiContainer.getOwner());
    containerWithJustGlobalViewProperties.setPermittedActions(apiContainer.getPermittedActions());
    containerWithJustGlobalViewProperties.setLinks(apiContainer.getLinks());
    // lists explicitly nullified
    containerWithJustGlobalViewProperties.setAttachments(null);
    containerWithJustGlobalViewProperties.setBarcodes(null);
    containerWithJustGlobalViewProperties.setExtraFields(null);
    containerWithJustGlobalViewProperties.setLocations(null);
    containerWithJustGlobalViewProperties.setParentContainers(null);

    // retrieved full-view container will contain more properties than created one
    assertNotEquals(containerWithJustGlobalViewProperties, apiContainer);

    // public view should be equal to one having just the known field populated
    apiContainer.clearPropertiesForPublicView();
    assertEquals(containerWithJustGlobalViewProperties, apiContainer);
  }
}
