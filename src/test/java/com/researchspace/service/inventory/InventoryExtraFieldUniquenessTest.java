package com.researchspace.service.inventory;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiExtraField.ExtraFieldTypeEnum;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.api.v1.model.ApiSubSampleInfo;
import com.researchspace.model.User;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * Manager-level coverage for the RSDEV-1066 uniqueness rule on ExtraField names across all
 * non-template Inventory entities (Sample, SubSample, Container; SampleTemplate ExtraFields are
 * covered alongside the SampleField tests in {@code SampleTemplatesApiManagerTest}). Exercises both
 * within-collection duplicates and label-collision (UI displayed name) cases. Cross-collection
 * (SampleField vs ExtraField on a live Sample) is verified here too.
 */
public class InventoryExtraFieldUniquenessTest extends SpringTransactionalTest {

  private User testUser;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    sampleDao.resetDefaultTemplateOwner();
    testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("uniqApi"));
    initialiseContentWithEmptyContent(testUser);
    assertTrue(testUser.isContentInitialized());
  }

  @Test
  public void containerCreateRejectsDuplicateExtraFieldNames() {
    ApiContainer toCreate = new ApiContainer();
    toCreate.setName(getRandomName(10));
    toCreate.setExtraFields(List.of(extraField("dup", "first"), extraField("dup", "second")));

    ApiRuntimeException are =
        assertThrows(
            ApiRuntimeException.class,
            () -> containerApiMgr.createNewApiContainer(toCreate, testUser));
    assertEquals("errors.inventory.field.duplicate.name", are.getMessage());
  }

  @Test
  public void containerCreateRejectsExtraFieldNamedAfterUILabel() {
    // RSDEV-1066: Container's displayed-label set includes "Type", so core-model's
    // verifyFieldNameAllowed rejects the field at addExtraField time with the legacy
    // reserved-name IAE.
    ApiContainer toCreate = new ApiContainer();
    toCreate.setName(getRandomName(10));
    toCreate.setExtraFields(List.of(extraField("Type", "collides with UI label")));

    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> containerApiMgr.createNewApiContainer(toCreate, testUser));
    assertEquals(
        "'Type' is not a valid name for a field, "
            + "as there is a default property with this name.",
        iae.getMessage());
  }

  @Test
  public void containerCreateTrimsBeforeCompare() {
    ApiContainer toCreate = new ApiContainer();
    toCreate.setName(getRandomName(10));
    toCreate.setExtraFields(List.of(extraField("Foo", "v1"), extraField(" Foo ", "v2")));

    ApiRuntimeException are =
        assertThrows(
            ApiRuntimeException.class,
            () -> containerApiMgr.createNewApiContainer(toCreate, testUser));
    assertEquals("errors.inventory.field.duplicate.name", are.getMessage());
  }

  @Test
  public void containerUpdateRejectsDuplicateExtraFieldNames() {
    ApiContainer toCreate = new ApiContainer();
    toCreate.setName(getRandomName(10));
    toCreate.setExtraFields(List.of(extraField("solo", "ok")));
    ApiContainer created = containerApiMgr.createNewApiContainer(toCreate, testUser);
    assertNotNull(created);

    ApiContainer update = new ApiContainer();
    update.setId(created.getId());
    update.setExtraFields(List.of(newExtraField("more", "first"), newExtraField("more", "second")));

    ApiRuntimeException are =
        assertThrows(
            ApiRuntimeException.class, () -> containerApiMgr.updateApiContainer(update, testUser));
    assertEquals("errors.inventory.field.duplicate.name", are.getMessage());
  }

  @Test
  public void subSampleUpdateRejectsDuplicateExtraFieldNames() {
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(testUser);
    ApiSubSampleInfo first = sample.getSubSamples().get(0);

    ApiSubSample update = new ApiSubSample();
    update.setId(first.getId());
    update.setExtraFields(List.of(newExtraField("dup", "a"), newExtraField("dup", "b")));

    ApiRuntimeException are =
        assertThrows(
            ApiRuntimeException.class, () -> subSampleApiMgr.updateApiSubSample(update, testUser));
    assertEquals("errors.inventory.field.duplicate.name", are.getMessage());
  }

  @Test
  public void sampleCreateRejectsDuplicateExtraFieldNames() {
    ApiSampleWithFullSubSamples toCreate = new ApiSampleWithFullSubSamples(getRandomName(10));
    toCreate.setExtraFields(List.of(extraField("dup", "a"), extraField("dup", "b")));

    ApiRuntimeException are =
        assertThrows(
            ApiRuntimeException.class, () -> sampleApiMgr.createNewApiSample(toCreate, testUser));
    assertEquals("errors.inventory.field.duplicate.name", are.getMessage());
  }

  @Test
  public void sampleRejectsExtraFieldNamedAfterTemplateFieldName() {
    // Template with a SampleField named "foo"
    ApiSampleTemplatePost templatePost = new ApiSampleTemplatePost();
    templatePost.setName("template-for-cross-collection-test");
    ApiInventoryEntityField sf = new ApiInventoryEntityField();
    sf.setName("foo");
    sf.setType(ApiFieldType.STRING);
    sf.setContent("v");
    templatePost.getFields().add(sf);
    ApiSampleTemplate template = sampleApiMgr.createSampleTemplate(templatePost, testUser);

    // Sample from template, plus an ExtraField also named "foo"
    ApiSampleWithFullSubSamples sample = new ApiSampleWithFullSubSamples(getRandomName(10));
    sample.setTemplateId(template.getId());
    sample.setExtraFields(List.of(extraField("foo", "collides-with-sample-field")));

    ApiRuntimeException are =
        assertThrows(
            ApiRuntimeException.class, () -> sampleApiMgr.createNewApiSample(sample, testUser));
    assertEquals("errors.inventory.field.duplicate.name", are.getMessage());
  }

  private ApiExtraField extraField(String name, String content) {
    ApiExtraField f = new ApiExtraField(ExtraFieldTypeEnum.TEXT);
    f.setName(name);
    f.setContent(content);
    return f;
  }

  private ApiExtraField newExtraField(String name, String content) {
    ApiExtraField f = extraField(name, content);
    f.setNewFieldRequest(true);
    return f;
  }
}
