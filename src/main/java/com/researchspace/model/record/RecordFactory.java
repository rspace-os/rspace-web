package com.researchspace.model.record;

import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.apache.commons.lang3.StringUtils.trim;

import com.researchspace.model.inventory.Basket;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.field.InventoryTimeField;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.StringUtils;

import com.researchspace.model.DefaultGroupNamingStrategy;
import com.researchspace.model.FileProperty;
import com.researchspace.model.Group;
import com.researchspace.model.IGroupNamingStrategy;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.elninventory.ListOfMaterials;
import com.researchspace.model.elninventory.MaterialUsage;
import com.researchspace.model.field.ChoiceFieldForm;
import com.researchspace.model.field.DateFieldForm;
import com.researchspace.model.field.Field;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.field.NumberFieldForm;
import com.researchspace.model.field.RadioFieldForm;
import com.researchspace.model.field.StringFieldForm;
import com.researchspace.model.field.TextFieldForm;
import com.researchspace.model.field.TimeFieldForm;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.model.inventory.InventoryFile;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SampleEntity;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.inventory.SubSampleName;
import com.researchspace.model.inventory.field.ExtraField;
import com.researchspace.model.inventory.field.ExtraLinkField;
import com.researchspace.model.inventory.field.ExtraNumberField;
import com.researchspace.model.inventory.field.ExtraTextField;
import com.researchspace.model.inventory.field.InventoryAttachmentField;
import com.researchspace.model.inventory.field.InventoryChoiceField;
import com.researchspace.model.inventory.field.InventoryChoiceFieldDef;
import com.researchspace.model.inventory.field.InventoryDateField;
import com.researchspace.model.inventory.field.InventoryNumberField;
import com.researchspace.model.inventory.field.InventoryRadioField;
import com.researchspace.model.inventory.field.InventoryRadioFieldDef;
import com.researchspace.model.inventory.field.InventoryReferenceField;
import com.researchspace.model.inventory.field.InventoryStringField;
import com.researchspace.model.inventory.field.InventoryTextField;
import com.researchspace.model.inventory.field.InventoryUriField;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.RSUnitDef;

/**
 * Factory class for creation of domain objects
 */
public class RecordFactory implements IRecordFactory {

  public static final String BASIC_DOCUMENT_FORM_NAME = "Basic Document";

  private IActiveUserStrategy modifiedByStrategy = IActiveUserStrategy.NULL;

  public void setModifiedByStrategy(IActiveUserStrategy modifiedByStrategy) {
    this.modifiedByStrategy = modifiedByStrategy;
  }

  private void checkArgs(Object... notNullArgs) {
    Validate.noNullElements(notNullArgs);
  }

  public StructuredDocument createStructuredDocument(String name, User createdBy,
      final RSForm form) {
    checkArgs(name, createdBy);
    StructuredDocument rc = new StructuredDocument(form);
    return addFieldsToStrucDocument(name, createdBy, form, rc);
  }


  @Override
  public StructuredDocument createStructuredDocument(String name, User createdBy, final RSForm form,
      ImportOverride override) {
    checkArgs(name, createdBy);
    StructuredDocument rc = new StructuredDocument(form, override);
    return addFieldsToStrucDocument(name, createdBy, form, rc);
  }

  private StructuredDocument addFieldsToStrucDocument(String name, User createdBy,
      final RSForm form, StructuredDocument rc) {
    for (FieldForm ft : form.getFieldForms()) {
      Field field = ft.createNewFieldFromForm();
      rc.addField(field);
    }
    rc.addType(RecordType.NORMAL);
    populateCoreFields(name, createdBy, rc);
    return rc;
  }

  public Snippet createSnippet(String name, String content, User createdBy) {
    checkArgs(name, createdBy);
    Snippet snippet = new Snippet(content);
    snippet.addType(RecordType.SNIPPET);
    populateCoreFields(name, createdBy, snippet);
    return snippet;
  }

  public RSForm createExperimentForm(String formName, String desc, User createdBy) {
    RSForm form = new RSForm(formName, desc, createdBy);
    form.setCurrent(true);

    String[] fnames = new String[]{"Method", "Objective", "Procedure", "Results", "Discussion",
        "Conclusion",
        "Comment"};
    int colindx = 0;
    for (String fName : fnames) {
      FieldForm fld = new TextFieldForm(fName);
      fld.setColumnIndex(colindx++);
      form.addFieldForm(fld);
    }
    form.setPublishingState(FormState.PUBLISHED);
    return form;
  }

  @Override
  public RSForm createFormForSeleniumTests(String formName, String desc, User createdBy) {

    Calendar formDate = Calendar.getInstance();
    formDate.set(2020, 0, 8, 0, 55, 15);

    RSForm seleniumForm = new RSForm(formName, desc, createdBy);
    seleniumForm.setCurrent(true);
    DateFieldForm date = new DateFieldForm("MyDate");
    date.setMaxValue(new Date().getTime());
    date.setMinValue(0);
    date.setDefaultDate(formDate.getTimeInMillis());
    date.setColumnIndex(0);
    seleniumForm.addFieldForm(date);

    ChoiceFieldForm cft = new ChoiceFieldForm("MyChoice");
    cft.setChoiceOptions("fieldChoices=a&fieldChoices=b&fieldChoices=c");
    cft.setDefaultChoiceOption("fieldSelectedChoices=a&fieldSelectedChoices=c");
    cft.setColumnIndex(1);
    seleniumForm.addFieldForm(cft);

    NumberFieldForm nft = new NumberFieldForm("MyNumber");
    nft.setMinNumberValue(0d);
    nft.setMaxNumberValue(10d);
    nft.setDefaultNumberValue(5d);
    nft.setColumnIndex(2);
    seleniumForm.addFieldForm(nft);

    RadioFieldForm rft = new RadioFieldForm("MyRadio");
    rft.setRadioOption("fieldRadios=a&fieldRadios=b");
    rft.setDefaultRadioOption("b");
    rft.setColumnIndex(3);
    seleniumForm.addFieldForm(rft);

    StringFieldForm sft = new StringFieldForm("MyString");
    sft.setColumnIndex(4);
    sft.setDefaultStringValue("string");
    sft.setIfPassword(false);
    seleniumForm.addFieldForm(sft);

    TextFieldForm tft = new TextFieldForm("MyText");
    tft.setColumnIndex(5);
    tft.setDefaultValue("<p>MyText</p>");
    seleniumForm.addFieldForm(tft);

    TimeFieldForm timeField = new TimeFieldForm("MyTime");
    timeField.setColumnIndex(6);
    timeField.setDefaultTime(formDate.getTimeInMillis());
    seleniumForm.addFieldForm(timeField);

    seleniumForm.setPublishingState(FormState.PUBLISHED);
    return seleniumForm;
  }

  private void populateCoreFields(String name, User createdBy, BaseRecord record) {
    name = abbreviate(trim(name), BaseRecord.DEFAULT_VARCHAR_LENGTH);
    record.setName(name);
    record.setCreatedBy(createdBy.getUsername());
    record.setOwner(createdBy);
    record.setModifiedBy(createdBy.getUsername(), modifiedByStrategy);
  }

  public Notebook createNotebook(String name, User createdBy) {
    checkArgs(name, createdBy);
    Notebook notebook = new Notebook();
    return doCreateNotebook(name, createdBy, notebook);
  }

  public Notebook createNotebook(String name, User createdBy, ImportOverride importOverride) {
    checkArgs(name, createdBy);
    Notebook notebook = new Notebook(importOverride);
    return doCreateNotebook(name, createdBy, notebook);
  }

  private Notebook doCreateNotebook(String name, User createdBy, Notebook notebook) {
    populateCoreFields(name, createdBy, notebook);
    notebook.addType(RecordType.NOTEBOOK);
    return notebook;
  }

  public Folder createFolder(String name, User createdBy) {
    checkArgs(name, createdBy);
    Folder rc = new Folder();
    return doCreateFolder(name, createdBy, rc);
  }

  public Folder createFolder(String name, User createdBy, ImportOverride override) {
    checkArgs(name, createdBy);
    Folder rc = new Folder(override);
    return doCreateFolder(name, createdBy, rc);
  }

  private Folder doCreateFolder(String name, User createdBy, Folder rc) {
    populateCoreFields(name, createdBy, rc);
    rc.addType(RecordType.FOLDER);
    return rc;
  }

  public Folder createSystemCreatedFolder(String name, User createdBy) {
    Folder folder = createFolder(name, createdBy);
    folder.addType(RecordType.SYSTEM);
    folder.setSystemFolder(true);
    return folder;
  }

  public Folder createRootFolder(String name, User createdBy) {
    Folder folder = createFolder(name, createdBy);
    folder.addType(RecordType.ROOT);
    folder.setSystemFolder(true);
    return folder;
  }

  @Override
  public RSForm createNewForm() {
    RSForm form = new RSForm();
    form.setCurrent(true);
    return form;
  }

  @Override
  public RSForm createBasicDocumentForm(User u) {
    RSForm basicDocForm = new RSForm(BASIC_DOCUMENT_FORM_NAME, "", u);
    TextFieldForm tft = new TextFieldForm("Data");
    basicDocForm.addFieldForm(tft);
    basicDocForm.setPublishingState(FormState.PUBLISHED);
    basicDocForm.setCurrent(true);
    basicDocForm.setSystemForm(true);
    return basicDocForm;
  }

  @Override
  public Folder createCommunalGroupFolder(Group grp, User createdBy) {
    IGroupNamingStrategy namingStrgy = new DefaultGroupNamingStrategy();
    return createACommunalGroupFolderUsingANamingStrategy(createdBy,
        () -> namingStrgy.getSharedGroupName(grp));
  }

  private Folder createACommunalGroupFolderUsingANamingStrategy(User createdBy,
      Supplier<String> folderNamingStrategy) {
    Folder folder = createFolder(folderNamingStrategy.get(), createdBy);
    folder.addType(RecordType.SHARED_GROUP_FOLDER_ROOT);
    folder.setSystemFolder(true);
    return folder;
  }

  @Override
  public Folder createCommunalGroupSnippetFolder(Group grp, User createdBy) {
    IGroupNamingStrategy namingStrgy = new DefaultGroupNamingStrategy();
    return createACommunalGroupFolderUsingANamingStrategy(createdBy,
        () -> namingStrgy.getSharedGroupSnippetName(grp));
  }

  public Record createAnyRecord(User user, String name) {
    StructuredDocument strucDoc = new StructuredDocument();
    strucDoc.addType(RecordType.NORMAL);
    populateCoreFields(name, user, strucDoc);
    return strucDoc;
  }

  public Folder createAnIndividualSharedFolderUsingANamingStrategy(User sharer,
      Supplier<String> namingStrategy) {
    Folder folder = createFolder(namingStrategy.get(), sharer);
    folder.setSystemFolder(true);
    folder.addType(RecordType.INDIVIDUAL_SHARED_FOLDER_ROOT);
    return folder;
  }

  @Override
  public Folder createIndividualSharedFolder(User sharer, User sharee) {
    IGroupNamingStrategy namingStrgy = new DefaultGroupNamingStrategy();
    return createAnIndividualSharedFolderUsingANamingStrategy(sharer,
        () -> namingStrgy.getIndividualSharedFolderName(sharer, sharee));
  }

  @Override
  public Folder createIndividualSharedSnippetsFolder(User sharer, User sharee) {
    IGroupNamingStrategy namingStrgy = new DefaultGroupNamingStrategy();
    return createAnIndividualSharedFolderUsingANamingStrategy(sharer,
        () -> namingStrgy.getIndividualSharedSnippetsFolderName(sharer, sharee));
  }

  /**
   * Creates a media root folder with correct properties set.
   */
  @Override
  public Folder createRootMediaFolder(User subject) {
    Folder mediaFolder = createSystemCreatedFolder(Folder.MEDIAROOT, subject);
    mediaFolder.addType(RecordType.ROOT_MEDIA);
    return mediaFolder;
  }

  @Override
  public Folder createApiInboxFolder(User subject) {
    Folder apiInboxFolder = createSystemCreatedFolder(Folder.API_INBOX_FOLDER_NAME, subject);
    apiInboxFolder.addType(RecordType.API_INBOX);
    return apiInboxFolder;
  }

  @Override
  public Folder createImportsFolder(User subject) {
    Folder importsFolder = createSystemCreatedFolder(Folder.IMPORTS_INBOX_FOLDER_NAME, subject);
    importsFolder.addType(RecordType.IMPORTS);
    return importsFolder;
  }

  @Override
  public SampleTemplate createComplexSampleTemplate(String templateName, String desc, User createdBy) {

    Calendar formDate = Calendar.getInstance();
    formDate.set(2020, 4, 4, 6, 55, 15);

    SampleTemplate template = createSampleTemplate(templateName, createdBy);
    template.setSubSampleAliases(SubSampleName.ALIQUOT);
    template.setDescription(desc);
    template.setStorageTempMin(QuantityInfo.of(3, RSUnitDef.CELSIUS));
    template.setStorageTempMax(QuantityInfo.of(10, RSUnitDef.CELSIUS));

    InventoryNumberField nf = new InventoryNumberField();
    nf.setName("MyNumber");
    nf.setFieldData("23");
    nf.setMandatory(true);
    template.addSampleField(nf);

    InventoryDateField df = new InventoryDateField();
    df.setName("MyDate");
    df.setFieldData("2020-10-01");
    template.addSampleField(df);

    InventoryStringField stringF = new InventoryStringField();
    stringF.setName("MyString");
    stringF.setFieldData("Default string value");
    template.addSampleField(stringF);

    InventoryTextField textF = new InventoryTextField();
    textF.setName("MyText");
    textF.setFieldData("Default text value");
    template.addSampleField(textF);

    InventoryUriField uriF = new InventoryUriField();
    uriF.setName("MyURL");
    uriF.setFieldData("https://www.google.com");
    template.addSampleField(uriF);

    InventoryReferenceField rff = new InventoryReferenceField();
    rff.setName("My reference");
    template.addSampleField(rff);

    InventoryAttachmentField aff = new InventoryAttachmentField();
    aff.setName("MyAttachment");
    template.addSampleField(aff);

    InventoryTimeField time = new InventoryTimeField();
    time.setName("MyTime");
    time.setFieldData("00:00");
    template.addSampleField(time);

    InventoryRadioFieldDef nodefaultsRadioFieldDef = new InventoryRadioFieldDef();
    nodefaultsRadioFieldDef.setRadioOptionsList(Arrays.asList("option1", "option2"));
    InventoryRadioField rff2 = new InventoryRadioField(nodefaultsRadioFieldDef, "radioField");
    template.addSampleField(rff2);

    InventoryChoiceFieldDef choiceFieldDef = new InventoryChoiceFieldDef();
    choiceFieldDef.setChoiceOptionsList(Arrays.asList("optionA", "optionB"));
    InventoryChoiceField choiceField = new InventoryChoiceField(choiceFieldDef, "choiceField");
    template.addSampleField(choiceField);

    return template;
  }

  @Override
  public Sample createSample(String name, User createdBy) {
    checkArgs(name, createdBy);

    Sample sample = new Sample();
    sample.setName(abbreviate(trim(name), BaseRecord.DEFAULT_VARCHAR_LENGTH));
    sample.setCreatedBy(createdBy.getUsername());
    sample.setOwner(createdBy);
    sample.setModifiedBy(createdBy.getUsername(), modifiedByStrategy);
    sample.setSubSampleAliases(SubSampleName.ALIQUOT);
    List<SubSample> defaultSubSampleList = new ArrayList<>();
    defaultSubSampleList.add(createSubSample(sample.getName(), createdBy, sample));
    sample.setSubSamples(defaultSubSampleList);
    sample.setDefaultUnitId(RSUnitDef.MILLI_LITRE.getId());

    return sample;
  }

  @Override
  public SampleTemplate createSampleTemplate(String name, User createdBy) {
    checkArgs(name, createdBy);

    SampleTemplate template = new SampleTemplate();
    template.setName(abbreviate(trim(name), BaseRecord.DEFAULT_VARCHAR_LENGTH));
    template.setCreatedBy(createdBy.getUsername());
    template.setOwner(createdBy);
    template.setModifiedBy(createdBy.getUsername(), modifiedByStrategy);
    template.setSubSampleAliases(SubSampleName.ALIQUOT);
    List<SubSample> defaultSubSampleList = new ArrayList<>();
    defaultSubSampleList.add(createSubSample(template.getName(), createdBy, template));
    template.setSubSamples(defaultSubSampleList);
    template.setDefaultUnitId(RSUnitDef.MILLI_LITRE.getId());

    return template;
  }


  @Override
  public Instrument createInstrument(String name, User createdBy,
      InstrumentTemplate instrumentTemplate) {
    checkArgs(name, createdBy);
    if (instrumentTemplate == null) {
      return createInstrument(name, createdBy);
    }
    Instrument instrument = (Instrument) instrumentTemplate.copyFromTemplate(createdBy);
    instrument.setName(abbreviate(trim(name), BaseRecord.DEFAULT_VARCHAR_LENGTH));
    instrument.setModifiedBy(createdBy.getUsername(), modifiedByStrategy);
    return instrument;
  }


  private Instrument createInstrument(String name, User createdBy) {
    checkArgs(name, createdBy);
    Instrument instrument = new Instrument();
    instrument.setName(abbreviate(trim(name), BaseRecord.DEFAULT_VARCHAR_LENGTH));
    instrument.setCreatedBy(createdBy.getUsername());
    instrument.setOwner(createdBy);
    instrument.setModifiedBy(createdBy.getUsername(), modifiedByStrategy);

    return instrument;
  }


  @Override
  public Sample createSample(String name, User createdBy, SampleTemplate sampleTemplate) {
    checkArgs(name, createdBy);
    Validate.notNull(sampleTemplate, "sampleTemplate must not be null");
    Sample sample = sampleTemplate.copyFromTemplate(createdBy);
    sample.setName(name);
    sample.setModifiedBy(createdBy.getUsername(), modifiedByStrategy);

    List<SubSample> defaultSubSampleList = new ArrayList<>();
    SubSample defaultSubSample = createSubSample(sample.getName(), createdBy, sample);
    if (sampleTemplate.getDefaultUnitId() != null) {
      defaultSubSample.getQuantity().setUnitId(sampleTemplate.getDefaultUnitId());
    }
    defaultSubSampleList.add(defaultSubSample);
    sample.setSubSamples(defaultSubSampleList);

    return sample;
  }

  @Override
  public SubSample createSubSample(String name, User createdBy, SampleEntity sample) {
    checkArgs(name, createdBy);

    SubSample subSample = new SubSample(sample);
    subSample.setName(abbreviate(trim(name), BaseRecord.DEFAULT_VARCHAR_LENGTH));
    subSample.setCreatedBy(createdBy.getUsername());
    subSample.setModifiedBy(createdBy.getUsername(), modifiedByStrategy);
    subSample.setQuantity(getDefaultQuantityForNewSubSample(sample));
    return subSample;
  }

  private QuantityInfo getDefaultQuantityForNewSubSample(SampleEntity sample) {
    RSUnitDef subSampleUnit;
    // getLinkedSampleTemplate() dispatches polymorphically through any Hibernate proxy, so no
    // unproxy/cast is needed (returns null for a template or a sample with no template).
    SampleTemplate template = sample.getLinkedSampleTemplate();
    if (template != null && template.getDefaultUnitId() != null) {
      subSampleUnit = RSUnitDef.getUnitById(template.getDefaultUnitId());
    } else if (sample.getQuantityInfo() != null) {
      subSampleUnit = RSUnitDef.getUnitById(sample.getQuantityInfo().getUnitId());
    } else {
      subSampleUnit = RSUnitDef.MILLI_LITRE; // fallback default when no template or quantity info is available
    }
    return QuantityInfo.of(BigDecimal.ONE, subSampleUnit);
  }

  @Override
  public Container createListContainer(String name, User createdBy) {
    checkArgs(name, createdBy);

    Container container = Container.createListContainer(true, true, true);
    container.setName(name);
    container.setName(abbreviate(trim(name), BaseRecord.DEFAULT_VARCHAR_LENGTH));
    container.setCreatedBy(createdBy.getUsername());
    container.setModifiedBy(createdBy.getUsername(), modifiedByStrategy);
    container.setOwner(createdBy);

    return container;
  }

  @Override
  public Container createWorkbench(User owner) {
    checkArgs(owner);

    Container workbench = new Container(ContainerType.WORKBENCH);
    workbench.setName("WB " + owner.getUsername());
    workbench.setCreatedBy(owner.getUsername());
    workbench.setModifiedBy(owner.getUsername());
    workbench.setOwner(owner);

    return workbench;
  }

  @Override
  public ExtraField createExtraField(String name, FieldType type, User createdBy,
      InventoryRecord invRec) {
    checkArgs(type, createdBy, invRec);

    ExtraField extraField = createExtraField(type);
    extraField.setInventoryRecord(invRec);

    if (StringUtils.isNotBlank(name)) {
      extraField.setName(abbreviate(trim(name), BaseRecord.DEFAULT_VARCHAR_LENGTH));
    }
    extraField.setCreatedBy(createdBy.getUsername());
    extraField.setModifiedBy(createdBy.getUsername(), modifiedByStrategy);

    return extraField;
  }

  @Override
  public ExtraField createExtraField(FieldType type) {
    checkArgs(type);
    if (FieldType.TEXT.equals(type)) {
      return new ExtraTextField();
    }
    if (FieldType.NUMBER.equals(type)) {
      return new ExtraNumberField();
    }
    if (FieldType.LINK.equals(type)) {
      return new ExtraLinkField();
    }
    throw new IllegalArgumentException(
        "asked for extra field of unsupported type: " + type.toString());
  }

  @Override
  public ListOfMaterials createListOfMaterials(String name, Field field) {
    ListOfMaterials lom = new ListOfMaterials();
    lom.setName(name);
    field.addListOfMaterials(lom);
    return lom;
  }

  @Override
  public MaterialUsage createMaterialUsage(ListOfMaterials parentLom, InventoryRecord invRec,
      QuantityInfo usedQuantity) {
    return new MaterialUsage(parentLom, invRec, usedQuantity);
  }

  @Override
  public Basket createBasket(String name, User owner) {
    Basket basket = new Basket();
    basket.setName(name);
    basket.setOwner(owner);
    return basket;
  }

  @Override
  public InventoryFile createInventoryFile(String fileName, FileProperty fileProperty,
      User createdBy) {
    InventoryFile inventoryFile = new InventoryFile(fileName, fileProperty);
    inventoryFile.setCreatedBy(createdBy.getUsername());
    return inventoryFile;
  }

  @Override
  public DigitalObjectIdentifier createDoiIdentifier(String identifier) {
    return new DigitalObjectIdentifier(identifier, null);
  }

}
