package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.User;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class InventoryTagApiManagerTest extends SpringTransactionalTest {

  private User testUser;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(testUser);
    assertTrue(testUser.isContentInitialized());
  }

  @Test
  public void getTags() {
    createASampleWithTagValue("SAMPLE_TAG", false, testUser);
    List<String> tags = inventoryTagsApiManager.getTagsForUser(testUser);
    assertTrue(tags.contains("SAMPLE_TAG"));
    createASampleWithTagValue("SAMPLE_CONTAINER_TAG", true, testUser);
    tags = inventoryTagsApiManager.getTagsForUser(testUser);
    assertTrue(tags.contains("SAMPLE_CONTAINER_TAG"));
    createASubSampleWithTagValue("SUBSAMPLE_TAG", false, testUser);
    tags = inventoryTagsApiManager.getTagsForUser(testUser);
    assertTrue(tags.contains("SUBSAMPLE_TAG"));
    assertTrue(tags.contains("SAMPLE_TAG"));
    createASubSampleWithTagValue("SUBSAMPLE_CONTAINER_TAG", true, testUser);
    tags = inventoryTagsApiManager.getTagsForUser(testUser);
    assertTrue(tags.contains("SUBSAMPLE_CONTAINER_TAG"));
    assertTrue(tags.contains("SAMPLE_TAG"));
    createBasicContainerForUserWithTag(testUser, "name", "CONTAINER_TAG");
    tags = inventoryTagsApiManager.getTagsForUser(testUser);
    assertTrue(tags.contains("SUBSAMPLE_TAG"));
    assertTrue(tags.contains("SUBSAMPLE_CONTAINER_TAG"));
    assertTrue(tags.contains("SAMPLE_TAG"));
    assertTrue(tags.contains("SAMPLE_CONTAINER_TAG"));
    assertTrue(tags.contains("CONTAINER_TAG"));
  }
}
