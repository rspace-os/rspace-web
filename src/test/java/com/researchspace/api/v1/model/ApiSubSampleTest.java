package com.researchspace.api.v1.model;

import static com.researchspace.core.testutil.CoreTestUtils.assertIllegalArgumentException;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.researchspace.model.User;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.testutils.SpringTransactionalTest;
import java.math.BigDecimal;
import org.junit.Test;

public class ApiSubSampleTest extends SpringTransactionalTest {

  User any = TestFactory.createAnyUser("any");

  @Test
  public void updateQuantity() {
    SubSample ss1 = getASubsample();
    // stored quantity
    ss1.setQuantity(TEN_ML().toQuantityInfo());

    ApiSubSample incoming = new ApiSubSample();
    incoming.setQuantity(ONE_ML());

    assertTrue(incoming.applyChangesToDatabaseSubSample(ss1, any));
    assertEquals(ONE_ML().toQuantityInfo(), ss1.getQuantity());
  }

  @Test
  public void updateQuantityNotCalledIfNoChange() {
    SubSample ss1 = getASubsample();
    // stored quantity
    ss1.setQuantity(TEN_ML().toQuantityInfo());

    ApiSubSample incoming = new ApiSubSample();
    incoming.setQuantity(TEN_ML());

    assertFalse(incoming.applyChangesToDatabaseSubSample(ss1, any));
  }

  @Test
  public void updateQuantityNotCalledIfIncomingNull() {
    SubSample ss1 = getASubsample();
    // stored quantity
    ss1.setQuantity(TEN_ML().toQuantityInfo());

    ApiSubSample incoming = new ApiSubSample();
    assertFalse(incoming.applyChangesToDatabaseSubSample(ss1, any));
  }

  @Test
  public void updateQuantityIfExistingIsNull() {
    SubSample ss1 = getASubsample();

    ApiSubSample incoming = new ApiSubSample();
    incoming.setQuantity(ONE_ML());
    assertTrue(incoming.applyChangesToDatabaseSubSample(ss1, any));
    assertEquals(ONE_ML().toQuantityInfo(), ss1.getQuantity());
  }

  @Test
  public void dontUpdateQuantityIfSameNumberDifferentPrecision() {
    SubSample ss1 = getASubsample();
    ss1.setQuantity(TEN_ML().toQuantityInfo());

    ApiSubSample incoming = new ApiSubSample();
    incoming.setQuantity(new ApiQuantityInfo(BigDecimal.valueOf(10.00), RSUnitDef.MILLI_LITRE));
    // 10 is now considered same as 10.00
    assertFalse(incoming.applyChangesToDatabaseSubSample(ss1, any));
  }

  private SubSample getASubsample() {
    Sample ss = TestFactory.createBasicSampleWithSubSamples(any, 5);
    return ss.getSubSamples().get(0);
  }

  @Test
  public void updateQuantityThrowsIAEIfIncompatibleUnits() {
    SubSample ss1 = getASubsample();
    // stored quantity
    ss1.setQuantity(TEN_ML().toQuantityInfo());

    // g are not compatible with ml
    ApiSubSample incoming = new ApiSubSample();
    incoming.setQuantity(ONE_GRAM());
    assertIllegalArgumentException(() -> incoming.applyChangesToDatabaseSubSample(ss1, any));
  }

  private ApiQuantityInfo ONE_GRAM() {
    return new ApiQuantityInfo(BigDecimal.ONE, RSUnitDef.GRAM);
  }

  private ApiQuantityInfo ONE_ML() {
    return new ApiQuantityInfo(BigDecimal.ONE, RSUnitDef.MICRO_LITRE);
  }

  private ApiQuantityInfo TEN_ML() {
    return new ApiQuantityInfo(BigDecimal.TEN, RSUnitDef.MILLI_LITRE);
  }

  @Test
  public void checkReducingSubSampleToLimitedView() {

    User user = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithExampleContent(user);
    ApiSampleWithFullSubSamples complexSample = createComplexSampleForUser(user);
    ApiSubSample apiSubSample =
        subSampleApiMgr.getApiSubSampleById(complexSample.getSubSamples().get(0).getId(), user);

    // create artificial subsample that only has properties expected in limited view, as defined in
    // RSINV-705
    ApiSubSample subSampleWithJustLimitedViewProperties = new ApiSubSample();
    // ApiInventoryRecordInfo level properties
    subSampleWithJustLimitedViewProperties.setId(apiSubSample.getId());
    subSampleWithJustLimitedViewProperties.setGlobalId(apiSubSample.getGlobalId());
    subSampleWithJustLimitedViewProperties.setName(apiSubSample.getName());
    subSampleWithJustLimitedViewProperties.setType(apiSubSample.getType());
    subSampleWithJustLimitedViewProperties.setOwner(apiSubSample.getOwner());
    subSampleWithJustLimitedViewProperties.setBarcodes(apiSubSample.getBarcodes());
    subSampleWithJustLimitedViewProperties.setCustomImage(apiSubSample.isCustomImage());
    subSampleWithJustLimitedViewProperties.setIconId(apiSubSample.getIconId());
    subSampleWithJustLimitedViewProperties.setTags(apiSubSample.getTags());
    subSampleWithJustLimitedViewProperties.setDescription(apiSubSample.getDescription());
    subSampleWithJustLimitedViewProperties.setPermittedActions(apiSubSample.getPermittedActions());
    subSampleWithJustLimitedViewProperties.setAttachments(null);
    subSampleWithJustLimitedViewProperties.setLinks(apiSubSample.getLinks());
    // ApiSubSampleInfo-level properties
    subSampleWithJustLimitedViewProperties.setParentContainers(apiSubSample.getParentContainers());
    subSampleWithJustLimitedViewProperties.setParentLocation(apiSubSample.getParentLocation());
    // ApiSubSampleInfoWithSampleInfo-level properties
    subSampleWithJustLimitedViewProperties.setSampleInfo(apiSubSample.getSampleInfo());
    // ApiSubSample level properties
    subSampleWithJustLimitedViewProperties.setExtraFields(null);
    subSampleWithJustLimitedViewProperties.setNotes(null);

    // retrieved full-view subsample will contain more properties than created one
    assertNotEquals(subSampleWithJustLimitedViewProperties, apiSubSample);

    // limited-view copy should be equal to one having just the known field populated
    apiSubSample.clearPropertiesForLimitedView();
    assertEquals(subSampleWithJustLimitedViewProperties, apiSubSample);
  }

  @Test
  public void checkReducingSubSampleToPublicView() {
    User user = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithExampleContent(user);
    ApiSampleWithFullSubSamples complexSample = createComplexSampleForUser(user);
    ApiSubSample apiSubSample =
        subSampleApiMgr.getApiSubSampleById(complexSample.getSubSamples().get(0).getId(), user);

    // create artificial subsample that only has properties expected in public view, as defined in
    // RSINV-212
    ApiSubSample subSampleWithJustGlobalViewProperties = new ApiSubSample();
    subSampleWithJustGlobalViewProperties.setId(apiSubSample.getId());
    subSampleWithJustGlobalViewProperties.setGlobalId(apiSubSample.getGlobalId());
    subSampleWithJustGlobalViewProperties.setType(apiSubSample.getType());
    subSampleWithJustGlobalViewProperties.setName(apiSubSample.getName());
    subSampleWithJustGlobalViewProperties.setOwner(apiSubSample.getOwner());
    subSampleWithJustGlobalViewProperties.setPermittedActions(apiSubSample.getPermittedActions());
    subSampleWithJustGlobalViewProperties.setLinks(apiSubSample.getLinks());
    // also has a reference to public info of parent sample
    Sample parentSample = sampleApiMgr.getSampleById(apiSubSample.getSampleInfo().getId(), user);
    ApiSampleWithoutSubSamples apiParentSamplePublicView =
        new ApiSampleWithoutSubSamples(parentSample);
    apiParentSamplePublicView.clearPropertiesForPublicView();
    subSampleWithJustGlobalViewProperties.setSampleInfo(apiParentSamplePublicView);
    // lists explicitly nullified
    subSampleWithJustGlobalViewProperties.setAttachments(null);
    subSampleWithJustGlobalViewProperties.setParentContainers(null);
    subSampleWithJustGlobalViewProperties.setBarcodes(null);
    subSampleWithJustGlobalViewProperties.setExtraFields(null);
    subSampleWithJustGlobalViewProperties.setNotes(null);

    // retrieved full-view container will contain more properties than created one
    assertNotEquals(subSampleWithJustGlobalViewProperties, apiSubSample);

    // public view should be equal to one having just the known field populated
    apiSubSample.clearPropertiesForPublicView();
    assertEquals(subSampleWithJustGlobalViewProperties, apiSubSample);
  }
}
