package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.researchspace.model.User;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Test;

public class ApiSampleTemplateTest extends SpringTransactionalTest {

  @Test
  public void checkReducingSampleTemplateToLimitedView() {

    User user = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithExampleContent(user);
    ApiSampleTemplate complexTemplate = createSampleTemplateWithRadioAndNumericFields(user);
    ApiSampleTemplate retrievedTemplate =
        sampleApiMgr.getApiSampleTemplateById(complexTemplate.getId(), user);

    // create artificial template that only has properties expected in limited view, as defined in
    // RSINV-705
    ApiSampleTemplate templateWithJustLimitedViewProperties = new ApiSampleTemplate();
    // ApiInventoryRecordInfo level properties
    templateWithJustLimitedViewProperties.setId(retrievedTemplate.getId());
    templateWithJustLimitedViewProperties.setGlobalId(retrievedTemplate.getGlobalId());
    templateWithJustLimitedViewProperties.setType(retrievedTemplate.getType());
    templateWithJustLimitedViewProperties.setName(retrievedTemplate.getName());
    templateWithJustLimitedViewProperties.setOwner(retrievedTemplate.getOwner());
    templateWithJustLimitedViewProperties.setBarcodes(retrievedTemplate.getBarcodes());
    templateWithJustLimitedViewProperties.setCustomImage(retrievedTemplate.isCustomImage());
    templateWithJustLimitedViewProperties.setIconId(retrievedTemplate.getIconId());
    templateWithJustLimitedViewProperties.setTags(retrievedTemplate.getTags());
    templateWithJustLimitedViewProperties.setDescription(retrievedTemplate.getDescription());
    templateWithJustLimitedViewProperties.setPermittedActions(
        retrievedTemplate.getPermittedActions());
    templateWithJustLimitedViewProperties.setAttachments(null);
    templateWithJustLimitedViewProperties.setLinks(retrievedTemplate.getLinks());
    // ApiSampleInfo-level properties
    templateWithJustLimitedViewProperties.setTemplate(retrievedTemplate.isTemplate());
    templateWithJustLimitedViewProperties.setTemplateId(retrievedTemplate.getTemplateId());
    templateWithJustLimitedViewProperties.setTemplateVersion(
        retrievedTemplate.getTemplateVersion());
    templateWithJustLimitedViewProperties.setTemplateImageAvailable(
        retrievedTemplate.isTemplateImageAvailable());
    templateWithJustLimitedViewProperties.setStorageTempMin(retrievedTemplate.getStorageTempMin());
    templateWithJustLimitedViewProperties.setStorageTempMax(retrievedTemplate.getStorageTempMax());
    templateWithJustLimitedViewProperties.setExpiryDate(retrievedTemplate.getExpiryDate());
    templateWithJustLimitedViewProperties.setSubSampleAlias(retrievedTemplate.getSubSampleAlias());
    templateWithJustLimitedViewProperties.setSampleSource(retrievedTemplate.getSampleSource());
    // ApiSampleInfoWithFields-level properties
    templateWithJustLimitedViewProperties.setFields(null);
    templateWithJustLimitedViewProperties.setExtraFields(null);
    // ApiSample level properties
    templateWithJustLimitedViewProperties.setSharedWith(null);
    templateWithJustLimitedViewProperties.setSubSamples(null);
    // ApiSampleTemplate level properties
    templateWithJustLimitedViewProperties.setDefaultUnitId(retrievedTemplate.getDefaultUnitId());

    // retrieved full-view template will contain more properties than created one
    assertNotEquals(templateWithJustLimitedViewProperties, retrievedTemplate);

    // limited-view copy should be equal to one having just the known field populated
    retrievedTemplate.clearPropertiesForLimitedView();
    assertEquals(templateWithJustLimitedViewProperties, retrievedTemplate);
  }

  @Test
  public void checkReducingSampleTemplateToPublicView() {
    User user = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithExampleContent(user);
    ApiSampleTemplate complexTemplate = createSampleTemplateWithRadioAndNumericFields(user);
    ApiSampleTemplate retrievedTemplate =
        sampleApiMgr.getApiSampleTemplateById(complexTemplate.getId(), user);

    // create artificial sample that only has properties expected in public view, as defined in
    // RSINV-212
    ApiSampleTemplate templateWithJustGlobalViewProperties = new ApiSampleTemplate();
    templateWithJustGlobalViewProperties.setId(retrievedTemplate.getId());
    templateWithJustGlobalViewProperties.setGlobalId(retrievedTemplate.getGlobalId());
    templateWithJustGlobalViewProperties.setType(retrievedTemplate.getType());
    templateWithJustGlobalViewProperties.setName(retrievedTemplate.getName());
    templateWithJustGlobalViewProperties.setOwner(retrievedTemplate.getOwner());
    templateWithJustGlobalViewProperties.setPermittedActions(
        retrievedTemplate.getPermittedActions());
    templateWithJustGlobalViewProperties.setLinks(retrievedTemplate.getLinks());
    // lists explicitly nullified
    templateWithJustGlobalViewProperties.setAttachments(null);
    templateWithJustGlobalViewProperties.setBarcodes(null);
    templateWithJustGlobalViewProperties.setFields(null);
    templateWithJustGlobalViewProperties.setExtraFields(null);
    templateWithJustGlobalViewProperties.setSharedWith(null);
    templateWithJustGlobalViewProperties.setSubSamples(null);

    // retrieved full-view sample will contain more properties than created one
    assertNotEquals(templateWithJustGlobalViewProperties, retrievedTemplate);

    // public view should be equal to one having just the known field populated
    retrievedTemplate.clearPropertiesForPublicView();
    assertEquals(templateWithJustGlobalViewProperties, retrievedTemplate);
  }
}
