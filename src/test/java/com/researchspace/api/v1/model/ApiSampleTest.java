package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.researchspace.model.User;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Test;

public class ApiSampleTest extends SpringTransactionalTest {

  @Test
  public void checkReducingSampleToLimitedView() {

    User user = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithExampleContent(user);
    ApiSampleWithFullSubSamples complexSample = createComplexSampleForUser(user);
    ApiSample apiSample = sampleApiMgr.getApiSampleById(complexSample.getId(), user);

    // create artificial sample that only has properties expected in limited view, as defined in
    // RSINV-705
    ApiSample sampleWithJustLimitedViewProperties = new ApiSample();
    // ApiInventoryRecordInfo level properties
    sampleWithJustLimitedViewProperties.setId(apiSample.getId());
    sampleWithJustLimitedViewProperties.setGlobalId(apiSample.getGlobalId());
    sampleWithJustLimitedViewProperties.setType(apiSample.getType());
    sampleWithJustLimitedViewProperties.setName(apiSample.getName());
    sampleWithJustLimitedViewProperties.setOwner(apiSample.getOwner());
    sampleWithJustLimitedViewProperties.setBarcodes(apiSample.getBarcodes());
    sampleWithJustLimitedViewProperties.setCustomImage(apiSample.isCustomImage());
    sampleWithJustLimitedViewProperties.setIconId(apiSample.getIconId());
    sampleWithJustLimitedViewProperties.setTags(apiSample.getTags());
    sampleWithJustLimitedViewProperties.setDescription(apiSample.getDescription());
    sampleWithJustLimitedViewProperties.setPermittedActions(apiSample.getPermittedActions());
    sampleWithJustLimitedViewProperties.setAttachments(null);
    sampleWithJustLimitedViewProperties.setLinks(apiSample.getLinks());
    // ApiSampleInfo-level properties
    sampleWithJustLimitedViewProperties.setTemplate(apiSample.isTemplate());
    sampleWithJustLimitedViewProperties.setTemplateId(apiSample.getTemplateId());
    sampleWithJustLimitedViewProperties.setTemplateVersion(apiSample.getTemplateVersion());
    sampleWithJustLimitedViewProperties.setTemplateImageAvailable(
        apiSample.isTemplateImageAvailable());
    sampleWithJustLimitedViewProperties.setStorageTempMin(apiSample.getStorageTempMin());
    sampleWithJustLimitedViewProperties.setStorageTempMax(apiSample.getStorageTempMax());
    sampleWithJustLimitedViewProperties.setExpiryDate(apiSample.getExpiryDate());
    // ApiSampleInfoWithFields-level properties
    sampleWithJustLimitedViewProperties.setFields(null);
    sampleWithJustLimitedViewProperties.setExtraFields(null);
    // ApiSample level properties
    sampleWithJustLimitedViewProperties.setSharedWith(null);
    sampleWithJustLimitedViewProperties.setSubSamples(null);

    // retrieved full-view sample will contain more properties than created one
    assertNotEquals(sampleWithJustLimitedViewProperties, apiSample);

    // limited-view copy should be equal to one having just the known field populated
    apiSample.clearPropertiesForLimitedView();
    assertEquals(sampleWithJustLimitedViewProperties, apiSample);
  }

  @Test
  public void checkReducingSampleToPublicView() {
    User user = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithExampleContent(user);
    ApiSampleWithFullSubSamples complexSample = createComplexSampleForUser(user);
    ApiSample apiSample = sampleApiMgr.getApiSampleById(complexSample.getId(), user);

    // create artificial sample that only has properties expected in public view, as defined in
    // RSINV-212
    ApiSample sampleWithJustGlobalViewProperties = new ApiSample();
    sampleWithJustGlobalViewProperties.setId(apiSample.getId());
    sampleWithJustGlobalViewProperties.setGlobalId(apiSample.getGlobalId());
    sampleWithJustGlobalViewProperties.setType(apiSample.getType());
    sampleWithJustGlobalViewProperties.setName(apiSample.getName());
    sampleWithJustGlobalViewProperties.setOwner(apiSample.getOwner());
    sampleWithJustGlobalViewProperties.setPermittedActions(apiSample.getPermittedActions());
    sampleWithJustGlobalViewProperties.setLinks(apiSample.getLinks());
    // lists explicitly nullified
    sampleWithJustGlobalViewProperties.setAttachments(null);
    sampleWithJustGlobalViewProperties.setBarcodes(null);
    sampleWithJustGlobalViewProperties.setFields(null);
    sampleWithJustGlobalViewProperties.setExtraFields(null);
    sampleWithJustGlobalViewProperties.setSharedWith(null);
    sampleWithJustGlobalViewProperties.setSubSamples(null);

    // retrieved full-view sample will contain more properties than created one
    assertNotEquals(sampleWithJustGlobalViewProperties, apiSample);

    // public view should be equal to one having just the known field populated
    apiSample.clearPropertiesForPublicView();
    assertEquals(sampleWithJustGlobalViewProperties, apiSample);
  }
}
