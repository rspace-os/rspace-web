package com.researchspace.service.impl;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiContainerInfo.ApiContainerGridLayoutConfig;
import com.researchspace.api.v1.model.ApiContainerLocationWithContent;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiExtraField.ExtraFieldTypeEnum;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.core.util.MediaUtils;
import com.researchspace.core.util.imageutils.ImageUtils;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.field.Field;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.permissions.PermissionFactory;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.ACLPropagationPolicy;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.UserFolderSetup;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/** Default initializer for example content */
@Service("contentInitializer")
@Profile({"run", "dev"})
public class ContentInitializerForDevRunManager extends AbstractContentInitializer {

  // default form/template names
  private static final String EXPERIMENT_FORM_NAME = "Experiment";
  public static final String SELENIUM_FORM_NAME = "Selenium";
  public static final String COMPLEX_SAMPLE_TEMPLATE_NAME = "Complex Sample Template";

  // default record/folder names
  public static final String EXAMPLE_EXPERIMENT_RECORD_NAME = "Experiment 1";
  private static final String EXAMPLE_SELENIUM_RECORD_NAME = "Editable2";
  private static final String LAB_BOOK_FOLDER_NAME = "Lab Book";
  public static final String OTHER_DATA_FOLDER_NAME = "Other Data";
  public static final String EXAMPLE_BASIC_SAMPLE_NAME = "Basic Sample";
  public static final String EXAMPLE_COMPLEX_SAMPLE_NAME = "Complex Sample #1";
  public static final String EXAMPLE_TOP_IMAGE_CONTAINER_NAME =
      "4-drawer storage unit (image container)";
  public static final String EXAMPLE_TOP_IMAGE_CONTAINER_ATTACHMENT_NAME =
      "storage_unit_assembly_instructions.txt";
  public static final String EXAMPLE_TOP_LIST_CONTAINER_NAME = "storage shelf #1 (list container)";

  // used for identifying this document in jmeter tests
  static final String EXAMPLE_TAG = "exampleExperimentTag";

  private @Autowired PermissionFactory permFactory;

  @Override
  protected void addCustomForms(User u) {
    List<RSForm> forms = formDao.getAll();
    boolean alreadyCreated =
        forms.stream().filter(t -> t.getName().equals(EXPERIMENT_FORM_NAME)).findAny().isPresent();
    if (!alreadyCreated) {
      RSForm form =
          recordFactory.createExperimentForm(
              EXPERIMENT_FORM_NAME, " A standard experiment description", u);
      form.getAccessControl().setWorldPermissionType(PermissionType.READ);
      formDao.save(form);
      try {
        createAndSaveIconEntity("Experiment32.png", form);
      } catch (IOException e) {
        log.error("error loading default icons", e);
      }
      formDao.save(form);

      RSForm seleniumForm =
          recordFactory.createFormForSeleniumTests(SELENIUM_FORM_NAME, "for selnium tests", u);
      seleniumForm.getAccessControl().setWorldPermissionType(PermissionType.READ);
      formDao.save(seleniumForm);
    }
  }

  @Override
  protected void addCustomSampleTemplates(User u) {
    PaginationCriteria<Sample> pg = PaginationCriteria.createDefaultForClass(Sample.class);
    pg.setGetAllResults();
    List<Sample> sampleTemplates = sampleDao.getTemplatesForUser(pg, null, null, u).getResults();
    boolean t1Created =
        sampleTemplates.stream()
            .filter(t -> t.getName().equals(COMPLEX_SAMPLE_TEMPLATE_NAME))
            .findAny()
            .isPresent();
    boolean t2Created =
        sampleTemplates.stream()
            .filter(t -> t.getName().equals("ComplexTemplate2"))
            .findAny()
            .isPresent();
    if (!t1Created) {
      createComplexSampleTemplate(u, COMPLEX_SAMPLE_TEMPLATE_NAME);
    }
    if (!t2Created) {
      createComplexSampleTemplate(u, "ComplexTemplate2");
    }
  }

  private void createComplexSampleTemplate(User u, String name) {
    Sample complexTemplate = recordFactory.createComplexSampleTemplate(name, "for testing", u);
    complexTemplate = sampleDao.persistSampleTemplate(complexTemplate);
    try {
      createAndSaveIconEntity("Experiment32.png", complexTemplate);
      createAndSavePreviewImage("Experiment 88.png", u, complexTemplate);
    } catch (IOException e) {
      log.error("error loading default icons", e);
    }
  }

  protected Folder doInit(User u, UserFolderSetup folders) {
    try {
      log.info("Initializing users's content - adding default example records");

      List<RSForm> forms = formDao.getAll();
      String formnames =
          StringUtils.join(
              forms.stream()
                  .map(f -> f.getName() + " - current? " + f.isCurrent())
                  .collect(Collectors.toList()),
              ",");
      log.info(" {} Forms available: {}", forms.size(), formnames);
      RSForm experimentForm = getCurrentFormByName(forms, EXPERIMENT_FORM_NAME);

      formMgr.addFormToUserCreateMenu(u, experimentForm.getId(), u);
      contentInitialiserUtils.delayForUniqueCreationTime();

      StructuredDocument structuredDocument2 =
          recordFactory.createStructuredDocument(EXAMPLE_EXPERIMENT_RECORD_NAME, u, experimentForm);
      structuredDocument2.setDocTag(EXAMPLE_TAG);
      structuredDocument2.setTagMetaData(EXAMPLE_TAG);
      for (Field field : structuredDocument2.getFields()) {
        if (field.getType().equals(FieldType.TEXT)) {
          field.setFieldData(getBigText());
        }
      }
      addChild(folders.getUserRoot(), structuredDocument2, u);
      // now add an image to strucDoc 2

      for (Field field : structuredDocument2.getTextFields()) {
        String url =
            loadImageReturnTextFieldLink(
                u, "StartUpData/Picture1.png", "Picture1.png", field.getId(), folders, 0, 0);
        if (!StringUtils.isEmpty(url)) {
          StringBuffer sb = new StringBuffer();
          sb.append(field.getFieldData());
          sb.append(url);
          field.setFieldData(sb.toString());
        }
        break;
      }
      recordDao.save(structuredDocument2);
      contentInitialiserUtils.delayForUniqueCreationTime();

      // sample lab records of type experiment
      Folder projectFolder = recordFactory.createFolder(LAB_BOOK_FOLDER_NAME, u);
      addChild(folders.getUserRoot(), projectFolder, u);
      contentInitialiserUtils.delayForUniqueCreationTime();

      StructuredDocument lab1 =
          recordFactory.createStructuredDocument("2012-01-30", u, experimentForm);
      addChild(projectFolder, lab1, u);
      contentInitialiserUtils.delayForUniqueCreationTime();

      StructuredDocument lab2 =
          recordFactory.createStructuredDocument("2012-01-31", u, experimentForm);
      addChild(projectFolder, lab2, u);
      contentInitialiserUtils.delayForUniqueCreationTime();

      // Only one field include.
      List<Long> fieldIds = List.of(structuredDocument2.getFields().get(0).getId());
      recordMgr.createTemplateFromDocument(
          structuredDocument2.getId(), fieldIds, u, "DemoTemplate");

      Folder otherFolder = recordFactory.createFolder(OTHER_DATA_FOLDER_NAME, u);
      addChild(folders.getUserRoot(), otherFolder, u);
      contentInitialiserUtils.delayForUniqueCreationTime();

      RSForm selenium = getCurrentFormByName(forms, SELENIUM_FORM_NAME);
      formMgr.addFormToUserCreateMenu(u, selenium.getId(), u);

      StructuredDocument otherRecord =
          recordFactory.createStructuredDocument(EXAMPLE_SELENIUM_RECORD_NAME, u, selenium);
      addChild(folders.getUserRoot(), otherRecord, u);
      contentInitialiserUtils.delayForUniqueCreationTime();

      createNotebooks(u, folders.getUserRoot(), forms);
      contentInitialiserUtils.delayForUniqueCreationTime();

      Folder anotherFolder = recordFactory.createFolder("Another Data", u);
      addChild(otherFolder, anotherFolder, u);
      contentInitialiserUtils.delayForUniqueCreationTime();

      StructuredDocument structuredDocument3 =
          recordFactory.createStructuredDocument("Other document", u, experimentForm);
      addChild(otherFolder, structuredDocument3, u);

      contentInitialiserUtils.delayForUniqueCreationTime();
      folderDao.save(folders.getUserRoot());
      initialiseMediaGalleryWithExampleImages(u, folders.getMediaImgExamples());

      initialiseInventoryWithExampleRecords(u);

      log.info("User's content initialized.");

    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      log.error(e.getMessage(), e);
    }

    return folderDao.get(folders.getUserRoot().getId());
  }

  private void initialiseInventoryWithExampleRecords(User u) {

    // create standalone complex sample
    PaginationCriteria<Sample> allTemplatesPagCrit =
        PaginationCriteria.createDefaultForClass(Sample.class);
    allTemplatesPagCrit.setResultsPerPage(Integer.MAX_VALUE);
    Optional<Sample> complexTemplateOpt =
        sampleDao.getTemplatesForUser(allTemplatesPagCrit, null, null, u).getResults().stream()
            .filter(st -> st.getName().contentEquals(COMPLEX_SAMPLE_TEMPLATE_NAME))
            .findFirst();

    if (complexTemplateOpt.isEmpty()) {
      throw new IllegalStateException("complex sample template not found for user " + u.getId());
    }
    Sample complexSampleTemplate = complexTemplateOpt.get();
    ApiSampleWithFullSubSamples complexSample = new ApiSampleWithFullSubSamples();
    complexSample.setName(EXAMPLE_COMPLEX_SAMPLE_NAME);
    complexSample.setTemplateId(complexSampleTemplate.getId());
    // add extra field to sample
    ApiExtraField extraText = new ApiExtraField();
    extraText.setName("My extra text");
    extraText.setContent("Lorem ipsum dolor sit amet...");
    complexSample.getExtraFields().add(extraText);
    // add subsample with extra field
    ApiSubSample complexSubSample = new ApiSubSample();
    ApiExtraField extraNumber = new ApiExtraField();
    extraNumber.setName("My extra number");
    extraNumber.setContent("3.141592");
    extraNumber.setType(ExtraFieldTypeEnum.NUMBER);
    complexSubSample.getExtraFields().add(extraNumber);
    complexSample.getSubSamples().add(complexSubSample);
    ApiSampleWithFullSubSamples createdSample = sampleApiMgr.createNewApiSample(complexSample, u);

    // add attached file to attachment field of complex sample
    try (InputStream txtFileIS =
        getClass().getResourceAsStream("/StartUpData/loremIpsem20para.txt")) {
      GlobalIdentifier attachmentFieldOid =
          new GlobalIdentifier(createdSample.getFields().get(6).getGlobalId());
      containerApiMgr.saveAttachment(attachmentFieldOid, "loremIpsem20para.txt", txtFileIS, u);
    } catch (IOException e) {
      log.warn("couldn't save attachment for complex sample field", e);
    }

    // create and save container hierarchy, with subcontainers pointing to parent from a start
    ApiContainer topContainer1 =
        new ApiContainer(EXAMPLE_TOP_LIST_CONTAINER_NAME, ContainerType.LIST);
    topContainer1.setRemoveFromParentContainerRequest(true);
    ApiContainer savedTopContainer1 = containerApiMgr.createNewApiContainer(topContainer1, u);
    ApiContainer subContainer1A = new ApiContainer("box #1 (list container)", ContainerType.LIST);
    subContainer1A.setParentContainer(savedTopContainer1);
    ApiContainer savedSubContainer1A = containerApiMgr.createNewApiContainer(subContainer1A, u);
    ApiContainer subContainer1B = new ApiContainer("24-well plate (6x4 grid)", ContainerType.GRID);
    subContainer1B.setGridLayout(new ApiContainerGridLayoutConfig(6, 4));
    subContainer1B.setParentContainer(savedTopContainer1);
    containerApiMgr.createNewApiContainer(subContainer1B, u);
    ApiContainer subContainer1C = new ApiContainer("96-well plate (12x8 grid)", ContainerType.GRID);
    subContainer1C.setGridLayout(new ApiContainerGridLayoutConfig(12, 8));
    subContainer1C.setParentContainer(savedTopContainer1);
    containerApiMgr.createNewApiContainer(subContainer1C, u);
    ApiContainer subContainer1AA = new ApiContainer("box A (list container)", ContainerType.LIST);
    subContainer1AA.setParentContainer(savedSubContainer1A);
    containerApiMgr.createNewApiContainer(subContainer1AA, u);
    ApiContainer subContainer1AB = new ApiContainer("box B (list container)", ContainerType.LIST);
    subContainer1AB.setParentContainer(savedSubContainer1A);
    containerApiMgr.createNewApiContainer(subContainer1AB, u);
    ApiContainer subContainer1AC = new ApiContainer("box C (list container)", ContainerType.LIST);
    subContainer1AC.setParentContainer(savedSubContainer1A);
    containerApiMgr.createNewApiContainer(subContainer1AC, u);

    // create basic sample with subsamples pointing to parent container from a start
    ApiSampleWithFullSubSamples basicSample = new ApiSampleWithFullSubSamples();
    basicSample.setName(EXAMPLE_BASIC_SAMPLE_NAME);
    ApiSubSample basicSubSample1 = new ApiSubSample();
    basicSubSample1.setParentContainer(savedSubContainer1A);
    basicSample.getSubSamples().add(basicSubSample1);
    sampleApiMgr.createNewApiSample(basicSample, u);

    // create top-level image container, with custom image and locations image, and pre-defined
    // locations
    ApiContainer imageContainer =
        new ApiContainer(EXAMPLE_TOP_IMAGE_CONTAINER_NAME, ContainerType.IMAGE);
    imageContainer.setRemoveFromParentContainerRequest(true);
    List<ApiContainerLocationWithContent> locations = new ArrayList<>();
    locations.add(new ApiContainerLocationWithContent(227, 114));
    locations.add(new ApiContainerLocationWithContent(227, 265));
    locations.add(new ApiContainerLocationWithContent(220, 422));
    locations.add(new ApiContainerLocationWithContent(230, 582));
    imageContainer.setLocations(locations);
    try (InputStream imageIS =
            getClass().getResourceAsStream("/StartUpData/inventory/storage_unit.jpg");
        InputStream locationsImageIS =
            getClass().getResourceAsStream("/StartUpData/inventory/storage_unit_locations.png")) {
      String imageBase64 =
          ImageUtils.getBase64DataImageFromImageBytes(IOUtils.toByteArray(imageIS), "jpg");
      imageContainer.setNewBase64Image(imageBase64);
      String locationsImageBase64 =
          ImageUtils.getBase64DataImageFromImageBytes(IOUtils.toByteArray(locationsImageIS), "png");
      imageContainer.setNewBase64LocationsImage(locationsImageBase64);
    } catch (IOException e) {
      log.warn("couldn't save image for default container", e);
    }
    imageContainer = containerApiMgr.createNewApiContainer(imageContainer, u);

    // add attached file to default image container
    try (InputStream instructionsTxtIS =
        getClass()
            .getResourceAsStream("/StartUpData/inventory/storage_unit_assembly_instructions.txt")) {
      containerApiMgr.saveAttachment(
          new GlobalIdentifier(imageContainer.getGlobalId()),
          EXAMPLE_TOP_IMAGE_CONTAINER_ATTACHMENT_NAME,
          instructionsTxtIS,
          u);
    } catch (IOException e) {
      log.warn("couldn't save attachment for default image container", e);
    }
  }

  private RSForm getCurrentFormByName(List<RSForm> forms, String name) {
    RSForm form =
        forms.stream()
            .filter(f -> f.getName().equals(name) && f.isCurrent())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("no current form with name: " + name));
    return form;
  }

  private void createNotebooks(User u, Folder rootForUser, List<RSForm> forms)
      throws IllegalAddChildOperation {

    Notebook sampleNotebook = recordFactory.createNotebook("Empty Notebook", u);
    addChild(rootForUser, sampleNotebook, u);
    contentInitialiserUtils.delayForUniqueCreationTime();

    Notebook sampleFullNotebook = recordFactory.createNotebook("Full Notebook", u);
    sampleFullNotebook.setDocTag("defaultFullNotebook,defaultContent");
    sampleFullNotebook.setTagMetaData("defaultFullNotebook,defaultContent");
    addChild(rootForUser, sampleFullNotebook, u);

    contentInitialiserUtils.delayForUniqueCreationTime();
    RSForm basicDocForm = getCurrentFormByName(forms, "Basic Document");
    IntStream.range(1, 4)
        .forEach(
            i -> {
              StructuredDocument entry = createEntry(u, basicDocForm, "Entry " + i);
              entry.setDocTag("defaultFullNotebookEntry,defaultContent");
              entry.setTagMetaData("defaultFullNotebookEntry,defaultContent");
              addChild(sampleFullNotebook, entry, u);
            });
  }

  private StructuredDocument createEntry(User u, RSForm basicDocForm, String name) {
    StructuredDocument entry1 = recordFactory.createStructuredDocument(name, u, basicDocForm);
    for (Field f : entry1.getFields()) {
      if (f.getType().equals(FieldType.TEXT)) {
        f.setFieldData(getBigNumbericImageText());
      }
    }
    contentInitialiserUtils.delayForUniqueCreationTime();
    return entry1;
  }

  /**
   * returns numbered paragraphs used in notebook and journal testing
   *
   * @return
   */
  private String getBigNumbericImageText() {
    final int n = 10;
    StringBuffer text = new StringBuffer();
    for (int i = 0; i < n; i++) {
      text.append(
          "<img src=\"/images/mainLogoN2.png\"><p><b>paragraph"
              + i
              + "</b> We have performed a biochemical and double-stranded RNA-mediated interference"
              + " (RNAi) analysis of the role of two chromosomal passenger proteins, inner"
              + " centromere protein (INCENP) and aurora B kinase, in cultured cells of Drosophila"
              + " melanogaster. INCENP and aurora B function is tightly interlinked. The two"
              + " proteins bind to each other in vitro, and DmINCENP is required for DmAurora B to"
              + " localize properly in mitosis and function as a histone H3 kinase. DmAurora B is"
              + " required for DmINCENP accumulation at centromeres and transfer to the spindle at"
              + " anaphase. RNAi for either protein dramatically inhibited the ability of cells to"
              + " achieve a normal metaphase chromosome alignment. Cells were not blocked in"
              + " mitosis, however, and entered an aberrant anaphase characterized by defects in"
              + " sister kinetochore disjunction and the presence of large amounts of amorphous"
              + " lagging chromatin. Anaphase A chromosome movement appeared to be normal, however"
              + " cytokinesis often failed. DmINCENP and DmAurora B are not required for the"
              + " correct localization of the kinesin-like protein Pavarotti (ZEN-4/CHO1/MKLP1) to"
              + " the midbody at telophase. These experiments reveal that INCENP is required for"
              + " aurora B kinase function and confirm that the chromosomal passengers have"
              + " essential roles in mitosis.</p>");
    }
    return text.toString();
  }

  private String getBigText() {
    String filePath = "StartUpData/loremIpsem20para.txt";
    Resource resource = new ClassPathResource(filePath);
    StringWriter writer = new StringWriter();
    try {
      IOUtils.copy(resource.getInputStream(), writer, Charset.defaultCharset());
    } catch (IOException e) {
      log.error("Error copying file {}.", filePath, e);
      return " small text , big text couldn't be loaded";
    }
    return writer.toString();
  }

  /**
   * Sets up some example images in the media gallery.
   *
   * @param u
   * @param targetFolder
   */
  private void initialiseMediaGalleryWithExampleImages(User u, Folder targetFolder) {
    String[] images = new String[] {"StartUpData/anaphase.jpg", "StartUpData/lemmings.gif"};
    for (String imagePath : images) {
      String name = FilenameUtils.getName(imagePath);
      loadImage(u, imagePath, name, targetFolder);
    }
  }

  protected void modifyFolderSetUp(UserFolderSetup folders, User subject) {
    Folder workspaceAPIInbox = createApiInbox(subject);
    contentInitialiserUtils.addChild(
        folders.getUserRoot(), workspaceAPIInbox, subject, ACLPropagationPolicy.NULL_POLICY);
    Folder importsInbox = createImportsInbox(subject);
    contentInitialiserUtils.addChild(
        folders.getUserRoot(), importsInbox, subject, ACLPropagationPolicy.NULL_POLICY);
    Folder imagesAPIInbox = createApiInbox(subject);
    Optional<BaseRecord> imgFolderOpt = getImageGalleryFolder(folders);
    if (imgFolderOpt.isPresent()) {
      Folder imageFolder = (Folder) imgFolderOpt.get();
      contentInitialiserUtils.addChild(
          imageFolder, imagesAPIInbox, subject, ACLPropagationPolicy.NULL_POLICY);
    }
  }

  private Folder createApiInbox(User subject) {
    Folder workspaceAPIInbox = recordFactory.createApiInboxFolder(subject);
    permFactory.setUpAclForIndividualInboxFolder(workspaceAPIInbox, subject);
    return workspaceAPIInbox;
  }

  private Folder createImportsInbox(User subject) {
    Folder importsInbox = recordFactory.createImportsFolder(subject);
    permFactory.setUpAclForIndividualInboxFolder(importsInbox, subject);
    return importsInbox;
  }

  private Optional<BaseRecord> getImageGalleryFolder(UserFolderSetup folders) {
    return folders.getMediaRoot().getChildrens().stream()
        .filter(f -> MediaUtils.IMAGES_MEDIA_FLDER_NAME.equals(f.getName()))
        .findFirst();
  }
}
