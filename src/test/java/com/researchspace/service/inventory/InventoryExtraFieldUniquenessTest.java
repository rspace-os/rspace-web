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
import com.researchspace.api.v1.model.ApiSample;
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
    sampleTemplateDao.resetDefaultTemplateOwner();
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

  // ---------- Sample: case-only, whitespace, reserved, delete-add, rename ----------

  @Test
  public void sampleCreateRejectsCaseOnlyDuplicateExtraFieldNames() {
    ApiSampleWithFullSubSamples toCreate = new ApiSampleWithFullSubSamples(getRandomName(10));
    toCreate.setExtraFields(List.of(extraField("Foo", "a"), extraField("foo", "b")));

    ApiRuntimeException are =
        assertThrows(
            ApiRuntimeException.class, () -> sampleApiMgr.createNewApiSample(toCreate, testUser));
    assertEquals("errors.inventory.field.duplicate.name", are.getMessage());
  }

  @Test
  public void sampleCreateTrimsBeforeCompare() {
    ApiSampleWithFullSubSamples toCreate = new ApiSampleWithFullSubSamples(getRandomName(10));
    toCreate.setExtraFields(List.of(extraField("Foo", "a"), extraField(" Foo ", "b")));

    ApiRuntimeException are =
        assertThrows(
            ApiRuntimeException.class, () -> sampleApiMgr.createNewApiSample(toCreate, testUser));
    assertEquals("errors.inventory.field.duplicate.name", are.getMessage());
  }

  @Test
  public void sampleCreateRejectsExtraFieldNamedAfterUILabel() {
    // Proves core-model verifyFieldNameAllowed is wired into the Sample create path.
    ApiSampleWithFullSubSamples toCreate = new ApiSampleWithFullSubSamples(getRandomName(10));
    toCreate.setExtraFields(List.of(extraField("Subsamples", "x")));

    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> sampleApiMgr.createNewApiSample(toCreate, testUser));
    assertTrue(
        iae.getMessage().contains("'Subsamples'"),
        "Expected message to mention 'Subsamples', got: " + iae.getMessage());
  }

  @Test
  public void sampleUpdateRejectsDuplicateExtraFieldNames() {
    ApiSampleWithFullSubSamples created = createSampleWithExtraField("solo", "ok");

    ApiSampleWithFullSubSamples update = new ApiSampleWithFullSubSamples();
    update.setId(created.getId());
    update.setExtraFields(List.of(newExtraField("more", "a"), newExtraField("more", "b")));

    ApiRuntimeException are =
        assertThrows(
            ApiRuntimeException.class, () -> sampleApiMgr.updateApiSample(update, testUser));
    assertEquals("errors.inventory.field.duplicate.name", are.getMessage());
  }

  @Test
  public void sampleUpdateRejectsCaseOnlyDuplicateExtraFieldNames() {
    ApiSampleWithFullSubSamples created = createSampleWithExtraField("solo", "ok");

    ApiSampleWithFullSubSamples update = new ApiSampleWithFullSubSamples();
    update.setId(created.getId());
    update.setExtraFields(List.of(newExtraField("Foo", "a"), newExtraField("foo", "b")));

    ApiRuntimeException are =
        assertThrows(
            ApiRuntimeException.class, () -> sampleApiMgr.updateApiSample(update, testUser));
    assertEquals("errors.inventory.field.duplicate.name", are.getMessage());
  }

  @Test
  public void sampleUpdateAllowsDeleteAndAddSameExtraFieldName() {
    ApiSampleWithFullSubSamples created = createSampleWithExtraField("A", "v1");
    Long existingId = created.getExtraFields().get(0).getId();

    ApiSampleWithFullSubSamples update = new ApiSampleWithFullSubSamples();
    update.setId(created.getId());
    ApiExtraField toDelete = new ApiExtraField();
    toDelete.setId(existingId);
    toDelete.setDeleteFieldRequest(true);
    update.setExtraFields(List.of(toDelete, newExtraField("A", "v2")));

    ApiSample updated = sampleApiMgr.updateApiSample(update, testUser);
    long activeAs =
        updated.getExtraFields().stream()
            .filter(ef -> "A".equals(ef.getName()))
            .filter(ef -> "v2".equals(ef.getContent()))
            .count();
    assertEquals(1L, activeAs);
  }

  @Test
  public void sampleUpdateRejectsRenameIntoExistingExtraField() {
    ApiSampleWithFullSubSamples created =
        createSampleWithExtraFields(extraField("A", "vA"), extraField("B", "vB"));
    Long bId =
        created.getExtraFields().stream()
            .filter(ef -> "B".equals(ef.getName()))
            .findFirst()
            .orElseThrow()
            .getId();

    ApiSampleWithFullSubSamples update = new ApiSampleWithFullSubSamples();
    update.setId(created.getId());
    ApiExtraField renamed = new ApiExtraField();
    renamed.setId(bId);
    renamed.setName("A");
    update.setExtraFields(List.of(renamed));

    ApiRuntimeException are =
        assertThrows(
            ApiRuntimeException.class, () -> sampleApiMgr.updateApiSample(update, testUser));
    assertEquals("errors.inventory.field.duplicate.name", are.getMessage());
  }

  // ---------- SubSample: case-only, whitespace, reserved, delete-add, rename ----------

  @Test
  public void subSampleUpdateRejectsCaseOnlyDuplicateExtraFieldNames() {
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(testUser);
    ApiSubSampleInfo first = sample.getSubSamples().get(0);

    ApiSubSample update = new ApiSubSample();
    update.setId(first.getId());
    update.setExtraFields(List.of(newExtraField("Foo", "a"), newExtraField("foo", "b")));

    ApiRuntimeException are =
        assertThrows(
            ApiRuntimeException.class, () -> subSampleApiMgr.updateApiSubSample(update, testUser));
    assertEquals("errors.inventory.field.duplicate.name", are.getMessage());
  }

  @Test
  public void subSampleUpdateTrimsBeforeCompare() {
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(testUser);
    ApiSubSampleInfo first = sample.getSubSamples().get(0);

    ApiSubSample update = new ApiSubSample();
    update.setId(first.getId());
    update.setExtraFields(List.of(newExtraField("Foo", "a"), newExtraField(" Foo ", "b")));

    ApiRuntimeException are =
        assertThrows(
            ApiRuntimeException.class, () -> subSampleApiMgr.updateApiSubSample(update, testUser));
    assertEquals("errors.inventory.field.duplicate.name", are.getMessage());
  }

  @Test
  public void subSampleUpdateRejectsExtraFieldNamedAfterUILabel() {
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(testUser);
    ApiSubSampleInfo first = sample.getSubSamples().get(0);

    ApiSubSample update = new ApiSubSample();
    update.setId(first.getId());
    update.setExtraFields(List.of(newExtraField("Notes", "x")));

    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> subSampleApiMgr.updateApiSubSample(update, testUser));
    assertTrue(
        iae.getMessage().contains("'Notes'"),
        "Expected message to mention 'Notes', got: " + iae.getMessage());
  }

  @Test
  public void subSampleUpdateAllowsDeleteAndAddSameExtraFieldName() {
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(testUser);
    ApiSubSampleInfo first = sample.getSubSamples().get(0);

    // Seed one extra field "A" on the subsample
    ApiSubSample seed = new ApiSubSample();
    seed.setId(first.getId());
    seed.setExtraFields(List.of(newExtraField("A", "v1")));
    ApiSubSample seeded = subSampleApiMgr.updateApiSubSample(seed, testUser);
    Long existingId =
        seeded.getExtraFields().stream()
            .filter(ef -> "A".equals(ef.getName()))
            .findFirst()
            .orElseThrow()
            .getId();

    // Now delete A and add a new A in the same PUT
    ApiSubSample update = new ApiSubSample();
    update.setId(first.getId());
    ApiExtraField toDelete = new ApiExtraField();
    toDelete.setId(existingId);
    toDelete.setDeleteFieldRequest(true);
    update.setExtraFields(List.of(toDelete, newExtraField("A", "v2")));

    ApiSubSample updated = subSampleApiMgr.updateApiSubSample(update, testUser);
    long activeAs =
        updated.getExtraFields().stream()
            .filter(ef -> "A".equals(ef.getName()))
            .filter(ef -> "v2".equals(ef.getContent()))
            .count();
    assertEquals(1L, activeAs);
  }

  @Test
  public void subSampleUpdateRejectsRenameIntoExistingExtraField() {
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(testUser);
    ApiSubSampleInfo first = sample.getSubSamples().get(0);

    // Seed two extra fields "A" and "B"
    ApiSubSample seed = new ApiSubSample();
    seed.setId(first.getId());
    seed.setExtraFields(List.of(newExtraField("A", "vA"), newExtraField("B", "vB")));
    ApiSubSample seeded = subSampleApiMgr.updateApiSubSample(seed, testUser);
    Long bId =
        seeded.getExtraFields().stream()
            .filter(ef -> "B".equals(ef.getName()))
            .findFirst()
            .orElseThrow()
            .getId();

    ApiSubSample update = new ApiSubSample();
    update.setId(first.getId());
    ApiExtraField renamed = new ApiExtraField();
    renamed.setId(bId);
    renamed.setName("A");
    update.setExtraFields(List.of(renamed));

    ApiRuntimeException are =
        assertThrows(
            ApiRuntimeException.class, () -> subSampleApiMgr.updateApiSubSample(update, testUser));
    assertEquals("errors.inventory.field.duplicate.name", are.getMessage());
  }

  // ---------- Container: case-only, whitespace (update), update reserved, delete-add, rename ----

  @Test
  public void containerCreateRejectsCaseOnlyDuplicateExtraFieldNames() {
    ApiContainer toCreate = new ApiContainer();
    toCreate.setName(getRandomName(10));
    toCreate.setExtraFields(List.of(extraField("Foo", "a"), extraField("foo", "b")));

    ApiRuntimeException are =
        assertThrows(
            ApiRuntimeException.class,
            () -> containerApiMgr.createNewApiContainer(toCreate, testUser));
    assertEquals("errors.inventory.field.duplicate.name", are.getMessage());
  }

  @Test
  public void containerUpdateTrimsBeforeCompare() {
    ApiContainer created = createContainerWithExtraField("solo", "ok");

    ApiContainer update = new ApiContainer();
    update.setId(created.getId());
    update.setExtraFields(List.of(newExtraField("Foo", "a"), newExtraField(" Foo ", "b")));

    ApiRuntimeException are =
        assertThrows(
            ApiRuntimeException.class, () -> containerApiMgr.updateApiContainer(update, testUser));
    assertEquals("errors.inventory.field.duplicate.name", are.getMessage());
  }

  @Test
  public void containerUpdateRejectsCaseOnlyDuplicateExtraFieldNames() {
    ApiContainer created = createContainerWithExtraField("solo", "ok");

    ApiContainer update = new ApiContainer();
    update.setId(created.getId());
    update.setExtraFields(List.of(newExtraField("Foo", "a"), newExtraField("foo", "b")));

    ApiRuntimeException are =
        assertThrows(
            ApiRuntimeException.class, () -> containerApiMgr.updateApiContainer(update, testUser));
    assertEquals("errors.inventory.field.duplicate.name", are.getMessage());
  }

  @Test
  public void containerUpdateRejectsExtraFieldNamedAfterUILabel() {
    ApiContainer created = createContainerWithExtraField("solo", "ok");

    ApiContainer update = new ApiContainer();
    update.setId(created.getId());
    update.setExtraFields(List.of(newExtraField("Type", "x")));

    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> containerApiMgr.updateApiContainer(update, testUser));
    assertTrue(
        iae.getMessage().contains("'Type'"),
        "Expected message to mention 'Type', got: " + iae.getMessage());
  }

  @Test
  public void containerUpdateAllowsDeleteAndAddSameExtraFieldName() {
    ApiContainer created = createContainerWithExtraField("A", "v1");
    Long existingId = created.getExtraFields().get(0).getId();

    ApiContainer update = new ApiContainer();
    update.setId(created.getId());
    ApiExtraField toDelete = new ApiExtraField();
    toDelete.setId(existingId);
    toDelete.setDeleteFieldRequest(true);
    update.setExtraFields(List.of(toDelete, newExtraField("A", "v2")));

    ApiContainer updated = containerApiMgr.updateApiContainer(update, testUser);
    long activeAs =
        updated.getExtraFields().stream()
            .filter(ef -> "A".equals(ef.getName()))
            .filter(ef -> "v2".equals(ef.getContent()))
            .count();
    assertEquals(1L, activeAs);
  }

  @Test
  public void containerUpdateRejectsRenameIntoExistingExtraField() {
    ApiContainer toCreate = new ApiContainer();
    toCreate.setName(getRandomName(10));
    toCreate.setExtraFields(List.of(extraField("A", "vA"), extraField("B", "vB")));
    ApiContainer created = containerApiMgr.createNewApiContainer(toCreate, testUser);
    Long bId =
        created.getExtraFields().stream()
            .filter(ef -> "B".equals(ef.getName()))
            .findFirst()
            .orElseThrow()
            .getId();

    ApiContainer update = new ApiContainer();
    update.setId(created.getId());
    ApiExtraField renamed = new ApiExtraField();
    renamed.setId(bId);
    renamed.setName("A");
    update.setExtraFields(List.of(renamed));

    ApiRuntimeException are =
        assertThrows(
            ApiRuntimeException.class, () -> containerApiMgr.updateApiContainer(update, testUser));
    assertEquals("errors.inventory.field.duplicate.name", are.getMessage());
  }

  // ---------- helpers ----------

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

  private ApiSampleWithFullSubSamples createSampleWithExtraField(String name, String content) {
    return createSampleWithExtraFields(extraField(name, content));
  }

  private ApiSampleWithFullSubSamples createSampleWithExtraFields(ApiExtraField... fields) {
    ApiSampleWithFullSubSamples toCreate = new ApiSampleWithFullSubSamples(getRandomName(10));
    toCreate.setExtraFields(List.of(fields));
    return sampleApiMgr.createNewApiSample(toCreate, testUser);
  }

  private ApiContainer createContainerWithExtraField(String name, String content) {
    ApiContainer toCreate = new ApiContainer();
    toCreate.setName(getRandomName(10));
    toCreate.setExtraFields(List.of(extraField(name, content)));
    return containerApiMgr.createNewApiContainer(toCreate, testUser);
  }
}
