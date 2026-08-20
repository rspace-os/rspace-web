package com.researchspace.model.test;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.model.AbstractUserOrGroupImpl;
import com.researchspace.model.Community;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.FieldAttachment;
import com.researchspace.model.FileProperty;
import com.researchspace.model.FileStoreRoot;
import com.researchspace.model.Group;
import com.researchspace.model.ImageBlob;
import com.researchspace.model.RecordAttachment;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.UserGroup;
import com.researchspace.model.UserPreference;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.elninventory.ListOfMaterials;
import com.researchspace.model.elninventory.MaterialUsage;
import com.researchspace.model.field.AttachmentFieldForm;
import com.researchspace.model.field.ChoiceFieldForm;
import com.researchspace.model.field.DateField;
import com.researchspace.model.field.DateFieldForm;
import com.researchspace.model.field.Field;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.field.NumberFieldForm;
import com.researchspace.model.field.RadioFieldForm;
import com.researchspace.model.field.ReferenceFieldForm;
import com.researchspace.model.field.StringFieldForm;
import com.researchspace.model.field.TextField;
import com.researchspace.model.field.TextFieldForm;
import com.researchspace.model.field.TimeFieldForm;
import com.researchspace.model.field.URIFieldForm;
import com.researchspace.model.field.UriField;
import com.researchspace.model.inventory.Barcode;
import com.researchspace.model.inventory.Basket;
import com.researchspace.model.inventory.BasketItem;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.ContainerLocation;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentEntity;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.model.inventory.InventoryFile;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SampleEntity;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.inventory.SubSampleNote;
import com.researchspace.model.inventory.field.ExtraNumberField;
import com.researchspace.model.inventory.field.ExtraTextField;
import com.researchspace.model.inventory.field.InventoryAttachmentField;
import com.researchspace.model.inventory.field.InventoryChoiceField;
import com.researchspace.model.inventory.field.InventoryChoiceFieldDef;
import com.researchspace.model.inventory.field.InventoryDateField;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.inventory.field.InventoryNumberField;
import com.researchspace.model.inventory.field.InventoryRadioField;
import com.researchspace.model.inventory.field.InventoryRadioFieldDef;
import com.researchspace.model.inventory.field.InventoryReferenceField;
import com.researchspace.model.inventory.field.InventoryStringField;
import com.researchspace.model.inventory.field.InventoryTextField;
import com.researchspace.model.inventory.field.InventoryTimeField;
import com.researchspace.model.inventory.field.InventoryUriField;
import com.researchspace.model.netfiles.ExternalStorageLocation;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.raid.UserRaid;
import com.researchspace.model.record.AbstractForm;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.model.record.RecordToFolder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

/**
 * Common class for hibernate-based tests.
 * <p>
 * To run these tests add this line (the tests use same DB credentials as for RSpace) mysql > grant
 * all on hibtest.* to 'rspacedbuser'@'localhost';
 */
public abstract class HibernateTest {

  static SessionFactory sf;
  static String testDbName = "hibtest";

  // this will be used for all test-cases
  @BeforeAll
  static void beforeAll() {
    sf = HibernateUtils.getSessionFactory(testDbName, recordClasses());
  }

  // enable other tests to use a differently configured session factory
  @AfterAll
  static void afterAll() {
    sf.close();
  }

  @BeforeEach
  void before() {
    dao = new TestDao(sf);
    testUser = createAndSaveAnyUser();
  }

  protected User createAndSaveAnyUser() {
    User u = TestFactory.createAnyUser(CoreTestUtils.getRandomName(10));
    u.setId(2L);
    return dao.save(u, User.class);
  }

  TestDao dao;
  User testUser;
  RecordFactory rf = new RecordFactory();

  protected void saveAssociatedEntities(Sample sample) {
    saveImageFileProperties(sample);
    saveSampleAttachedFiles(sample);
    saveSampleFieldAttachments(sample);

    SubSample originalSS = sample.getActiveSubSamples().get(0);
    saveImageFileProperties(originalSS);
  }

  protected void saveImageFileProperties(InventoryRecord invRec) {
    if (invRec.getImageFileProperty() == null) {
      return; // record to save prob has no images
    }
    dao.save(invRec.getImageFileProperty().getRoot(), FileStoreRoot.class);
    dao.save(invRec.getThumbnailFileProperty().getRoot(), FileStoreRoot.class);
    dao.save(invRec.getImageFileProperty(), FileProperty.class);
    dao.save(invRec.getThumbnailFileProperty(), FileProperty.class);
    if (invRec.isContainer()) {
      Container c = (Container) invRec;
      if (c.getLocationsImageFileProperty() != null) {
        dao.save(c.getLocationsImageFileProperty().getRoot(), FileProperty.class);
        dao.save(c.getLocationsImageFileProperty(), FileProperty.class);
      }
    }
  }

  protected void saveSampleAttachedFiles(Sample sample) {
    for (InventoryFile invFile : sample.getAttachedFiles()) {
      dao.save(invFile.getFileProperty().getRoot(), FileStoreRoot.class);
      dao.save(invFile.getFileProperty(), FileProperty.class);
    }
  }

  protected void saveSampleFieldAttachments(Sample sample) {
    for (InventoryEntityField sf : sample.getActiveFields()) {
      if (FieldType.ATTACHMENT.equals(sf.getType())) {
        InventoryFile attachedFile = sf.getAttachedFile();
        if (attachedFile != null) {
          dao.save(attachedFile.getFileProperty().getRoot(), FileStoreRoot.class);
          dao.save(attachedFile.getFileProperty(), FileProperty.class);
        }
      }
    }
  }

  protected void saveParentTemplateForSample(Sample sample) {
    saveRadioAndChoiceDefinitions(dao, sample.getSTemplate());
    dao.save(sample.getSTemplate(), SampleTemplate.class);
  }

  //util method to save radio/choice definitions
  protected void saveRadioAndChoiceDefinitions(TestDao dao2, SampleEntity complexSample) {
    complexSample.getActiveFields().stream().filter(f -> FieldType.CHOICE.equals(f.getType()))
        .map(field -> (InventoryChoiceField) field)
        .forEach(cf -> dao2.save(cf.getChoiceDef(), InventoryChoiceFieldDef.class));
    complexSample.getActiveFields().stream().filter(f -> FieldType.RADIO.equals(f.getType()))
        .map(field -> (InventoryRadioField) field)
        .forEach(cf -> dao2.save(cf.getRadioDef(), InventoryRadioFieldDef.class));
  }


  // these classes must all be mapped for hibernate to work with any of them
  // but it's quicker to run than searching packages for entities.
  // add new classes as needed.
  static Class<?>[] recordClasses() {
    return new Class[]{DateFieldForm.class, NumberFieldForm.class, TextFieldForm.class,
        StringFieldForm.class, TimeFieldForm.class, RadioFieldForm.class, ChoiceFieldForm.class,
        ReferenceFieldForm.class, DateField.class, TextField.class, Field.class,
        UserPreference.class, Community.class, UserGroup.class, Group.class, RecordAttachment.class,
        RecordToFolder.class, FileProperty.class, FileStoreRoot.class, EcatMediaFile.class,
        EcatImage.class, EcatDocumentFile.class, ImageBlob.class, Record.class,
        FieldAttachment.class, ExternalStorageLocation.class, NfsFileSystem.class,
        NfsFileStore.class, Folder.class, Role.class, BaseRecord.class, RSForm.class,
        FieldForm.class, StructuredDocument.class, AbstractUserOrGroupImpl.class, User.class,
        InventoryEntityField.class, InventoryNumberField.class, InventoryStringField.class,
        InventoryDateField.class, InventoryTimeField.class, InventoryReferenceField.class,
        InventoryTextField.class, InventoryChoiceField.class, InventoryRadioField.class,
        InventoryAttachmentField.class, AttachmentFieldForm.class, URIFieldForm.class,
        UriField.class, InventoryUriField.class, SampleEntity.class, SampleTemplate.class,
        Sample.class, SubSample.class, AbstractForm.class,
        Barcode.class, DigitalObjectIdentifier.class, ExtraNumberField.class, ExtraTextField.class,
        SubSampleNote.class, Container.class, ContainerLocation.class, InventoryFile.class,
        InventoryRadioFieldDef.class, InventoryChoiceFieldDef.class, ListOfMaterials.class,
        MaterialUsage.class, DMPUser.class, Basket.class, BasketItem.class, UserRaid.class,
        InstrumentEntity.class, InstrumentTemplate.class, Instrument.class };
  }

}