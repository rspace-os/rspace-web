package com.researchspace.service.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.User;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class InventoryTagApiManagerTest extends SpringTransactionalTest {

  private @Autowired InventoryTagApiManager inventoryTagsApiManager;
  private User testUser;

  @BeforeEach
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
    assertThat(tags).contains("SAMPLE_TAG");
    createASampleWithTagValue("SAMPLE_CONTAINER_TAG", true, testUser);
    tags = inventoryTagsApiManager.getTagsForUser(testUser);
    assertThat(tags).contains("SAMPLE_CONTAINER_TAG");
    createASubSampleWithTagValue("SUBSAMPLE_TAG", false, testUser);
    tags = inventoryTagsApiManager.getTagsForUser(testUser);
    assertThat(tags).contains("SUBSAMPLE_TAG");
    assertThat(tags).contains("SAMPLE_TAG");
    createASubSampleWithTagValue("SUBSAMPLE_CONTAINER_TAG", true, testUser);
    tags = inventoryTagsApiManager.getTagsForUser(testUser);
    assertThat(tags).contains("SUBSAMPLE_CONTAINER_TAG");
    assertThat(tags).contains("SAMPLE_TAG");
    createBasicContainerForUserWithTag(testUser, "name", "CONTAINER_TAG");
    tags = inventoryTagsApiManager.getTagsForUser(testUser);
    assertThat(tags).contains("SUBSAMPLE_TAG");
    assertThat(tags).contains("SUBSAMPLE_CONTAINER_TAG");
    assertThat(tags).contains("SAMPLE_TAG");
    assertThat(tags).contains("SAMPLE_CONTAINER_TAG");
    assertThat(tags).contains("CONTAINER_TAG");
  }
}
