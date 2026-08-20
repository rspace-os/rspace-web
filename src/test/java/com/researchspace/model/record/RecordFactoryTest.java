package com.researchspace.model.record;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.Group;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.inventory.SubSampleName;
import com.researchspace.model.inventory.field.ExtraField;
import com.researchspace.model.inventory.field.ExtraLinkField;
import com.researchspace.model.units.RSUnitDef;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RecordFactoryTest {

  IRecordFactory factoryAPI;
  private User user, otherUser;

  @Before
  public void setUp() throws Exception {
    factoryAPI = new RecordFactory();
    user = TestFactory.createAnyUser("user");
    otherUser = TestFactory.createAnyUser("otherUser");
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void createExtraFieldLinkReturnsExtraLinkField() {
    ExtraField field = factoryAPI.createExtraField(FieldType.LINK);
    assertTrue(field instanceof ExtraLinkField);
    assertEquals(FieldType.LINK, field.getType());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCreateRichTextDocumentFailsIfARgsNull() {
    factoryAPI.createFolder(null, user);
  }

  @Test
  public void testCreateStructuredDocument() {
    RSForm form =
        factoryAPI.createExperimentForm("name", "desc", TestFactory.createAnyUser("user"));
    StructuredDocument structuredDoc =
        factoryAPI.createStructuredDocument("\n notroot\n  \n", user, form);
    assertFalse(structuredDoc.hasType(RecordType.ROOT));
    assertNotNull(structuredDoc.getForm());
    assertEquals(form.getNumAllFields(), structuredDoc.getFields().size());
    assertCoreDataNotNull(structuredDoc);
    // control chars and newlines are stripped.
    assertEquals("notroot", structuredDoc.getName());
  }

  @Test
  public void testCreateSnippet() {

    String testContent = "test content";
    Snippet snippet = factoryAPI.createSnippet("testSnippet", testContent, user);
    assertEquals(testContent, snippet.getContent());
    assertFalse(snippet.hasType(RecordType.ROOT));
    assertCoreDataNotNull(snippet);
  }

  @Test
  public void testCreateSsytemCreatedFolder() {
    Folder folder = factoryAPI.createSystemCreatedFolder("notroot", user);
    assertTrue(folder.isSystemFolder());
    assertEquals(0, folder.getChildren().size());
    assertCoreDataNotNull(folder);
  }

  @Test
  public void testCreateFolder() {
    Folder folder = factoryAPI.createFolder("notroot", user);
    assertFalse(folder.hasType(RecordType.ROOT));
    assertEquals(0, folder.getChildren().size());
    assertCoreDataNotNull(folder);
  }

  private void assertCoreDataNotNull(BaseRecord baseRecord) {
    assertNotNull(baseRecord.getCreationDate());
    assertNotNull(baseRecord.getCreatedBy());
    assertNotNull(baseRecord.getName());
  }

  @Test
  public void testCreateRootFolder() {
    Folder folder = factoryAPI.createRootFolder("notroot", user);
    assertTrue(folder.hasType(RecordType.ROOT));
    assertTrue(folder.isRootFolder());
    assertTrue(folder.isRootFolderForUser(user));
    assertEquals(0, folder.getChildren().size());
    assertCoreDataNotNull(folder);
  }

  @Test
  public void testCreateCommunalGrpFolder() {
    user.addRole(Role.PI_ROLE);
    Group grp = TestFactory.createAnyGroup(user, new User[] {});
    Folder f = factoryAPI.createCommunalGroupFolder(grp, otherUser);
    assertTrue(f.hasType(RecordType.SHARED_GROUP_FOLDER_ROOT));
  }

  @Test
  public void testCreateNewSample() {
    Sample sample = factoryAPI.createSample("test sample", user);
    assertNotNull(sample.getCreationDate());
    assertEquals(1, sample.getSubSamples().size());
    assertEquals(1, sample.getActiveSubSamples().size());
    assertEquals(0, sample.getActiveFields().size());
  }

  @Test
  public void testCreateInstrumentWithoutTemplate() {
    Instrument instrument = factoryAPI.createInstrument("test instrument", user, null);
    assertNotNull(instrument);
    assertEquals("test instrument", instrument.getName());
    assertEquals(user, instrument.getOwner());
    assertEquals(user.getUsername(), instrument.getCreatedBy());
    assertEquals(user.getUsername(), instrument.getModifiedBy());
    assertNull(instrument.getInstrumentTemplate());
    assertNull(instrument.getTemplateLinkedVersion());
    assertEquals(0, instrument.getActiveFields().size());
  }

  @Test
  public void testCreateInstrumentFromTemplate() {
    Instrument baseInstrument = factoryAPI.createInstrument("base", user, null);
    InstrumentTemplate template = (InstrumentTemplate) baseInstrument.copyToTemplate(user);

    Instrument fromTemplate = factoryAPI.createInstrument("derived", otherUser, template);

    assertNotNull(fromTemplate);
    assertEquals("derived", fromTemplate.getName());
    assertEquals(otherUser, fromTemplate.getOwner());
    assertEquals(otherUser.getUsername(), fromTemplate.getCreatedBy());
    assertEquals(otherUser.getUsername(), fromTemplate.getModifiedBy());
    assertEquals(template, fromTemplate.getInstrumentTemplate());
    assertEquals(template.getVersion(), fromTemplate.getTemplateLinkedVersion());
  }

  @Test
  public void testCreateSampleTemplate() {
    SampleTemplate template = factoryAPI.createSampleTemplate("my template", user);
    assertNotNull(template);
    assertEquals("my template", template.getName());
    assertTrue(template.isTemplate());
    // default subsample alias is ALIQUOT
    assertEquals(SubSampleName.ALIQUOT.getDisplayName(), template.getSubSampleAlias());
    // one default subsample
    assertEquals(1, template.getSubSamples().size());
    assertEquals(1, template.getActiveSubSamplesCount());
    // default unit is MILLI_LITRE
    assertEquals(RSUnitDef.MILLI_LITRE.getId(), template.getDefaultUnitId());
    // owner and createdBy set
    assertEquals(user, template.getOwner());
    assertEquals(user.getUsername(), template.getCreatedBy());
  }

  @Test
  public void testCreateSampleFromTemplate() {
    SampleTemplate template = factoryAPI.createSampleTemplate("tmpl", user);

    Sample sample = factoryAPI.createSample("derived sample", otherUser, template);

    assertNotNull(sample);
    assertEquals("derived sample", sample.getName());
    assertFalse(sample.isTemplate());
    assertEquals(template, sample.getSTemplate());
    assertEquals(template.getVersion(), sample.getSTemplateLinkedVersion());
    assertEquals(otherUser, sample.getOwner());
    assertEquals(otherUser.getUsername(), sample.getCreatedBy());
    assertEquals(otherUser.getUsername(), sample.getModifiedBy());
  }

  @Test(expected = NullPointerException.class)
  public void testCreateSampleFromTemplateRejectsNullTemplate() {
    factoryAPI.createSample("fail", user, null);
  }

  @Test
  public void testCreateSubSampleUsesSampleTemplateDefaultUnit() {
    SampleTemplate template = factoryAPI.createSampleTemplate("tmpl", user);
    template.setDefaultUnitId(RSUnitDef.GRAM.getId());
    Sample sample = factoryAPI.createSample("derived", otherUser, template);

    SubSample subSample = factoryAPI.createSubSample("ss", otherUser, sample);

    // a Sample backed by a template adopts the template's default unit
    assertEquals(RSUnitDef.GRAM.getId(), subSample.getQuantity().getUnitId());
  }

  @Test
  public void testCreateSubSampleForTemplateDoesNotCastToSample() {
    SampleTemplate template = factoryAPI.createSampleTemplate("tmpl", user);

    // a SampleTemplate is a SampleEntity but NOT a Sample: createSubSample must not
    // attempt a Sample cast, and falls back to the default unit with no quantity info
    SubSample subSample = factoryAPI.createSubSample("ss", user, template);

    assertNotNull(subSample.getQuantity());
    assertEquals(RSUnitDef.MILLI_LITRE.getId(), subSample.getQuantity().getUnitId());
  }

  @Test
  public void createComplexSampleTemplateUsesProvidedDescription() {
    SampleTemplate template =
        factoryAPI.createComplexSampleTemplate("tmpl", "my custom description", user);
    assertEquals("my custom description", template.getDescription());
  }
}
