package com.researchspace.service.impl;

import com.researchspace.core.util.imageutils.ImageUtils;
import com.researchspace.dao.FieldDao;
import com.researchspace.dao.RSChemElementDao;
import com.researchspace.files.service.FileStore;
import com.researchspace.linkedelements.FieldParser;
import com.researchspace.linkedelements.RichTextUpdater;
import com.researchspace.model.ChemElementsFormat;
import com.researchspace.model.ChemSearchedItem;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.FileProperty;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.dtos.chemistry.ChemElementDataDto;
import com.researchspace.model.dtos.chemistry.ChemicalDataDTO;
import com.researchspace.model.dtos.chemistry.ChemicalExportFormat;
import com.researchspace.model.dtos.chemistry.ChemicalExportType;
import com.researchspace.model.dtos.chemistry.ChemicalImageDTO;
import com.researchspace.model.dtos.chemistry.ChemicalSearchResultsDTO;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import com.researchspace.model.field.Field;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.BaseRecordAdaptable;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.AuditManager;
import com.researchspace.service.EcatChemistryFileManager;
import com.researchspace.service.FieldManager;
import com.researchspace.service.OperationFailedMessageGenerator;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.chemistry.ChemistryProvider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("rsChemElementManager")
@Transactional
public class RSChemElementManagerImpl extends GenericManagerImpl<RSChemElement, Long>
    implements RSChemElementManager {

  private static final Logger logger = LoggerFactory.getLogger(RSChemElementManagerImpl.class);
  protected @Autowired AuditManager auditManager;
  private @Autowired RSChemElementDao rsChemElementDao;
  private @Autowired EcatChemistryFileManager chemistryFileManager;
  private @Autowired BaseRecordAdaptable recordAdapter;
  private @Autowired FieldManager fieldManager;
  private @Autowired IPermissionUtils permissionUtils;
  private @Autowired FieldParser fieldParser;
  private @Autowired FieldManager fieldMgr;
  private @Autowired FieldDao fieldDao;
  private @Autowired RichTextUpdater textUpdater;
  private @Autowired OperationFailedMessageGenerator authMsgGenerator;
  private @Autowired IPermissionUtils permUtils;
  private @Autowired @Qualifier("compositeFileStore") FileStore fileStore;
  private @Autowired ChemistryProvider chemistryProvider;

  public RSChemElementManagerImpl(@Autowired RSChemElementDao dao) {
    super(dao);
  }

  public void setFileStore(FileStore fileStore) {
    this.fileStore = fileStore;
  }

  @Override
  public RSChemElement get(long id, User user) {
    RSChemElement chem = rsChemElementDao.get(id);
    Optional<BaseRecord> br = recordAdapter.getAsBaseRecord(chem);
    if (br.isEmpty() || !permissionUtils.isPermitted(br.get(), PermissionType.READ, user)) {
      throw new AuthorizationException(
          "Unauthorised attempt by user ["
              + user.getUsername()
              + "] to access chem element with id ["
              + id
              + "]");
    }
    return chem;
  }

  @Override
  public RSChemElement getRevision(long id, Long revisionId, User user) {
    RSChemElement latestChem = get(id, user); // checks permission
    if (revisionId == null) {
      return latestChem;
    }

    RSChemElement revisionChem = null;
    AuditedEntity<RSChemElement> audited =
        auditManager.getObjectForRevision(RSChemElement.class, id, revisionId);
    if (audited != null) {
      revisionChem = audited.getEntity();
      if (revisionChem.getImageFileProperty() != null) {
        revisionChem.getImageFileProperty().getFileName(); // init lazy-loaded relation
      }
    }
    return revisionChem;
  }

  @Override
  public RSChemElement save(RSChemElement rsChemElement, User user) {
    RSChemElement persisted = chemistryProvider.save(rsChemElement);
    return rsChemElementDao.save(persisted);
  }

  @Override
  public void delete(long id, User user) throws Exception {
    rsChemElementDao.remove(id);
  }

  public List<ChemSearchedItem> search(String chemQuery, String searchType, User subject) {
    ChemicalSearchResultsDTO searchResults = chemistryProvider.search(chemQuery, searchType);
    List<RSChemElement> elements = rsChemElementDao.getChemElementsForChemIds(searchResults);
    return getChemSearchedItems(elements, subject);
  }

  /**
   * Given a list of RSChemElement raw search hits, filters by permission, deletion and presence of
   * link in text field to ensure that returned hits are relevant.
   *
   * @param lstChemElements
   * @param user
   * @return
   */
  private List<ChemSearchedItem> getChemSearchedItems(
      List<RSChemElement> lstChemElements, User user) {
    List<ChemSearchedItem> lstChemSearchedItems = new ArrayList<>();

    for (RSChemElement rsChemElement : lstChemElements) {

      addGalleryFileToSearchResults(user, lstChemSearchedItems, rsChemElement);
      Long fieldId = rsChemElement.getParentId();
      // the field will be null for snippets and gallery files that haven't been inserted into a doc
      // yet.
      Field field = getField(user, rsChemElement, fieldId);
      if (field == null) {
        continue;
      }
      if (fieldParser.hasChemElement(rsChemElement.getId(), field.getFieldData())) {
        addStructuredDocumentToSearchResults(
            user, lstChemSearchedItems, rsChemElement, field.getStructuredDocument());
      }
    }

    return lstChemSearchedItems;
  }

  private Field getField(User user, RSChemElement rsChemElement, Long fieldId) {
    Optional<Field> field;
    if (fieldId == null) {
      logger.warn(
          "Field id is null for chemElement {}; maybe is a snippet or gallery file?",
          rsChemElement.getId());
      return null;
    }
    try {
      field = fieldManager.get(fieldId, user);
    } catch (DataAccessException ex) {
      logger.warn(
          "Could not find this field with id [{}] for chemElement [{}],  maybe is a snippet or"
              + " gallery file?",
          fieldId,
          rsChemElement.getId());
      return null;
    }
    // sanity check
    if (field.isEmpty() || field.get().getFieldData() == null) {
      return null;
    }
    return field.get();
  }

  private void addGalleryFileToSearchResults(
      User user, List<ChemSearchedItem> lstChemSearchedItems, RSChemElement rsChemElement) {
    if (rsChemElement.getEcatChemFileId() != null) {
      EcatChemistryFile chemistryFile = chemistryFileManager.get(rsChemElement.getEcatChemFileId());
      if (permissionUtils.isPermitted(chemistryFile, PermissionType.READ, user)) {
        ChemSearchedItem chemSearchedItem = new ChemSearchedItem();
        chemSearchedItem.setChemId(rsChemElement.getId());
        chemSearchedItem.setRecordId(chemistryFile.getId());
        chemSearchedItem.setRecordName(chemistryFile.getName());
        chemSearchedItem.setRecord(chemistryFile);
        if (!containsEcatChemFileId(lstChemSearchedItems, chemistryFile.getId())) {
          lstChemSearchedItems.add(chemSearchedItem);
        }
      }
    }
  }

  /**
   * Helper method to check whether a list of {@link ChemSearchedItem} already contains an {@link
   * ChemSearchedItem} with the given {@link EcatChemistryFile} id, this is to prevent duplication
   * of the same gallery file appearing in search results.
   *
   * @param chemSearchedItems The list opf ChemSearchedItem to check
   * @param ecatChemFileId the EcatChemistryFile id to to check with
   * @return true if the list contains an ChemSearchedItem with the EcatChemistryFileId or false
   *     otherwise
   */
  public boolean containsEcatChemFileId(
      final List<ChemSearchedItem> chemSearchedItems, final Long ecatChemFileId) {
    return chemSearchedItems.stream().anyMatch(o -> o.getRecordId().equals(ecatChemFileId));
  }

  private void addStructuredDocumentToSearchResults(
      User user,
      List<ChemSearchedItem> lstChemSearchedItems,
      RSChemElement rsChemElement,
      StructuredDocument structuredDocument) {
    if (structuredDocument != null
        && !structuredDocument.isDeleted()
        && !((BaseRecord) structuredDocument).isDeletedForUser(user)) {
      if (permissionUtils.isPermitted((BaseRecord) structuredDocument, PermissionType.READ, user)) {
        ChemSearchedItem chemSearchedItem = new ChemSearchedItem();
        chemSearchedItem.setChemId(rsChemElement.getId());
        chemSearchedItem.setRecordId(structuredDocument.getId());
        chemSearchedItem.setRecordName(structuredDocument.getName());
        chemSearchedItem.setRecord(structuredDocument);
        if (!lstChemSearchedItems.contains(chemSearchedItem)) {
          lstChemSearchedItems.add(chemSearchedItem);
        }
      }
    }
  }

  @Override
  public Optional<ElementalAnalysisDTO> getInfo(RSChemElement chemElement) {
    return chemistryProvider.getProperties(chemElement);
  }

  @Override
  public RSChemElement saveChemElement(ChemicalDataDTO chemicalDataDTO, User subject)
      throws IOException {

    Field field =
        getFieldAndAssertAuthorised(
            chemicalDataDTO.getFieldId(), subject, "save chemical structure");
    byte[] decodedBytes =
        ImageUtils.getImageBytesFromBase64DataImage(chemicalDataDTO.getImageBase64());

    RSChemElement rsChemElement = null;
    if (chemicalDataDTO.getRsChemElementId() != null) {
      rsChemElement = get(chemicalDataDTO.getRsChemElementId(), subject);
    } else if (chemicalDataDTO.getEcatChemFileId() != null) {
      rsChemElement =
          rsChemElementDao.getChemElementFromChemistryGalleryFile(
              chemicalDataDTO.getEcatChemFileId());
    }

    if (rsChemElement != null) {
      rsChemElement.setDataImage(decodedBytes);
      rsChemElement.setChemElements(chemicalDataDTO.getChemElements());
      rsChemElement.setChemElementsFormat(ChemElementsFormat.MRV);
      // need to save field, if we need to remove a previous revision id
      boolean revisionRemoved =
          textUpdater.removeRevisionsFromChemWithId(field, rsChemElement.getId() + "");
      if (revisionRemoved) {
        fieldDao.save(field);
      }
    } else {
      rsChemElement =
          RSChemElement.builder()
              .dataImage(decodedBytes)
              .chemElements(chemicalDataDTO.getChemElements())
              .chemElementsFormat(ChemElementsFormat.MRV)
              .parentId(field.getId())
              .record(field.getStructuredDocument())
              .build();
    }
    // Set Chemistry file related params if chem file id isn't null
    if (chemicalDataDTO.getEcatChemFileId() != null) {
      rsChemElement.setEcatChemFileId(chemicalDataDTO.getEcatChemFileId());
      rsChemElement.setParentId(field.getId());
      rsChemElement.setRecord(field.getStructuredDocument());
      EcatChemistryFile chemistryFile =
          chemistryFileManager.get(chemicalDataDTO.getEcatChemFileId(), subject);
      field.addMediaFileLink(chemistryFile);
      fieldMgr.save(field, subject);
    }

    return save(rsChemElement, subject);
  }

  @Override
  public RSChemElement saveChemImage(ChemicalImageDTO chemicalImageDTO, User user)
      throws IOException {
    Long chemElemId = chemicalImageDTO.getChemId();
    RSChemElement rsChemElement = get(chemElemId, user);
    byte[] decodedBytes =
        ImageUtils.getImageBytesFromBase64DataImage(chemicalImageDTO.getImageBase64());
    InputStream imageStream = new ByteArrayInputStream(decodedBytes);

    return saveChemImagePng(rsChemElement, imageStream, user);
  }

  @Override
  public RSChemElement saveChemImagePng(RSChemElement rsChemElement, InputStream imageIS, User user)
      throws IOException {
    String fileName = "chem_" + rsChemElement.getId() + ".png";
    FileProperty fp = fileStore.createAndSaveFileProperty("chemImages", user, fileName, imageIS);
    rsChemElement.setImageFileProperty(fp);
    return save(rsChemElement, user);
  }

  @Override
  public RSChemElement saveChemImageSvg(RSChemElement rsChemElement, InputStream imageIS, User user)
      throws IOException {
    String fileName = "chem_" + rsChemElement.getId() + ".svg";
    FileProperty fp = fileStore.createAndSaveFileProperty("chemImages", user, fileName, imageIS);
    rsChemElement.setImageFileProperty(fp);
    return save(rsChemElement, user);
  }

  @Override
  public RSChemElement createChemElement(ChemElementDataDto chemElementDataDto, User subject)
      throws IOException {
    EcatChemistryFile chemistryFile =
        chemistryFileManager.get(chemElementDataDto.getEcatChemFileId(), subject);
    chemElementDataDto.setFileName(chemistryFile.getFileName());
    Field field =
        getFieldAndAssertAuthorised(
            chemElementDataDto.getFieldId(), subject, "save chemical structure");

    // Query for RSChemElements generated by upload to gallery i.e their field ids are null or
    // previously created elements with
    // the same ecatChemFileId
    RSChemElement result = getRsChemElement(chemElementDataDto.getEcatChemFileId(), subject);

    if (result != null) {
      result.setParentId(field.getId());
      result.setRecord(field.getStructuredDocument());
      result = save(result, subject);
      // need to save field, if we need to remove a previous revision id
      boolean revisionRemoved =
          textUpdater.removeRevisionsFromChemWithId(field, result.getId() + "");
      if (revisionRemoved) {
        fieldDao.save(field);
      }
    } else {
      result = generateNewRsChemElement(chemElementDataDto, subject, chemistryFile, field);
    }
    // Add chemistry file to field attachments
    if (field.addMediaFileLink(chemistryFile).isPresent()) {
      fieldMgr.save(field, subject);
    }

    return result;
  }

  /**
   * Helper method to retrieve previously created chem elements to reduce the need to call the
   * chemistry web service to generate a new chemical image
   *
   * @param ecatChemFileId the id of the chemistry file
   * @param user the currently logged in user
   * @return the retrieved chem element or null if none could be found.
   */
  private RSChemElement getRsChemElement(Long ecatChemFileId, User user) {
    // Check for basic chem element created on gallery upload, which will have a null fieldId
    RSChemElement result = rsChemElementDao.getChemElementFromChemistryGalleryFile(ecatChemFileId);
    if (result == null) {
      // Get any previously saved chem elements with the same ecatChemFileId and create a new
      // rschemelement with it.
      List<RSChemElement> chemElements = getRSChemElementsLinkedToFile(ecatChemFileId, user);
      if (!chemElements.isEmpty()) {
        RSChemElement toCopy = chemElements.get(0);
        result =
            RSChemElement.builder()
                .ecatChemFileId(ecatChemFileId)
                .dataImage(toCopy.getDataImage())
                .imageFileProperty(toCopy.getImageFileProperty())
                .chemElements(chemistryProvider.convert(toCopy.getChemElements()))
                .chemElementsFormat(toCopy.getChemElementsFormat())
                .build();
      } else {
        return null;
      }
    }
    return result;
  }

  private RSChemElement generateNewRsChemElement(
      ChemElementDataDto chemElementDataDto,
      User subject,
      EcatChemistryFile chemistryFile,
      Field field)
      throws IOException {
    RSChemElement result;
    byte[] image =
        chemistryProvider.exportToImage(
            chemistryFile.getChemString(),
            ChemicalExportFormat.builder()
                .height(chemElementDataDto.getFullHeight())
                .width(chemElementDataDto.getFullWidth())
                .build());

    RSChemElement newChemElement =
        RSChemElement.builder()
            .parentId(field.getId())
            .dataImage(image)
            .ecatChemFileId(chemistryFile.getId())
            .chemElements(chemistryProvider.convert(chemistryFile.getChemString()))
            .chemElementsFormat(chemFormat(chemElementDataDto))
            .record(field.getStructuredDocument())
            .build();
    // Save chem element here so the id isn't null when saving chem image
    newChemElement = save(newChemElement, subject);
    result = saveChemImagePng(newChemElement, new ByteArrayInputStream(image), subject);

    return result;
  }

  private ChemElementsFormat chemFormat(ChemElementDataDto chemElementDataDto) {
    return ChemElementsFormat.valueOf(chemElementDataDto.getChemElementsFormat().toUpperCase());
  }

  @Override
  public RSChemElement generateRsChemElementFromChemistryFile(
      EcatChemistryFile chemistryFile, long fieldId, User user) throws IOException {
    ChemElementDataDto dto =
        ChemElementDataDto.builder()
            .chemString(chemistryProvider.convert(chemistryFile.getChemString()))
            .chemElementsFormat("MRV")
            .ecatChemFileId(chemistryFile.getId())
            .fileName(chemistryFile.getName())
            .fieldId(fieldId)
            .fullWidth(1000)
            .fullHeight(1000)
            .build();
    return createChemElement(dto, user);
  }

  @Override
  public void updateAssociatedChemElements(
      EcatChemistryFile newChemistryFile, FileProperty newFileProperty, User user)
      throws IOException {
    newChemistryFile.setChemString(chemistryProvider.convert(fileStore.findFile(newFileProperty)));
    List<RSChemElement> rsChemElements =
        getRSChemElementsLinkedToFile(newChemistryFile.getId(), user);
    for (RSChemElement rsChemElement : rsChemElements) {
      updateChemElement(newChemistryFile, rsChemElement, user);
    }
    updateChemImages(
        newChemistryFile,
        rsChemElements,
        new ChemicalExportFormat(ChemicalExportType.PNG, 1000, 1000, null),
        user);
  }

  @Override
  public List<RSChemElement> updateChemImages(
      EcatChemistryFile chemistryFile,
      List<RSChemElement> rsChemElements,
      ChemicalExportFormat chemicalExportFormat,
      User user)
      throws IOException {
    byte[] newImageBytes = getUpdatedImageBytes(chemistryFile, chemicalExportFormat);
    for (RSChemElement chemElement : rsChemElements) {
      chemElement.setDataImage(newImageBytes);
      saveChemImagePng(chemElement, new ByteArrayInputStream(newImageBytes), user);
      save(chemElement, user);
    }
    return rsChemElements;
  }

  private byte[] getUpdatedImageBytes(
      EcatChemistryFile chemistryFile, ChemicalExportFormat exportFormat) {
    return chemistryProvider.exportToImage(chemistryFile.getChemString(), exportFormat);
  }

  @Override
  public RSChemElement updateChemElement(
      EcatChemistryFile newChemistryFile, RSChemElement toUpdate, User user) throws IOException {
    // Set chemId and reactionId as null so we create a new entry in the chemical structures table
    toUpdate.setChemId(null);
    toUpdate.setReactionId(null);
    toUpdate.setRgroupId(null);
    toUpdate.setChemElements(chemistryProvider.convert(newChemistryFile.getChemString()));
    toUpdate.setChemElementsFormat(ChemElementsFormat.MRV);
    return save(toUpdate, user);
  }

  @Override
  public List<RSChemElement> getRSChemElementsLinkedToFile(Long ecatChemFileId, User user) {
    return rsChemElementDao.getChemElementsFromChemFileId(ecatChemFileId);
  }

  @Override
  public List<RSChemElement> getAllRSChemElementsByField(Long fieldId, User user) {
    return rsChemElementDao.getAllChemElementsFromField(fieldId);
  }

  @Override
  public void generateRsChemElementForNewlyUploadedChemistryFile(EcatChemistryFile chemFile,
      User subject) throws IOException {

    // Create Basic Chem Element in order for searching of gallery files to work
    RSChemElement rsChemElement =
        RSChemElement.builder()
            .chemElements(
                chemistryProvider.convert(chemFile.getChemString()))
            .chemElementsFormat(ChemElementsFormat.MRV)
            .ecatChemFileId(chemFile.getId())
            .build();

    ChemicalExportFormat format =
        ChemicalExportFormat.builder()
            .exportType(ChemicalExportType.PNG)
            .height(1000)
            .width(1000)
            .build();
    generateRsChemExportBytes(format, rsChemElement);
    saveChemImagePng(
        rsChemElement, new ByteArrayInputStream(rsChemElement.getDataImage()), subject);
  }

  /**
   * Generate the byte representation of the given {@link RSChemElement} in the given format format
   * is currently limited to either PNG or JPEG export with a specified size (width x height)
   *
   * @param format the ChemicalExportFormat to generate the bytes with
   * @param rsChemElement the {@link RSChemElement} to generate the bytes of
   * @return the byte array representation of the rschemelement
   */
  private RSChemElement generateRsChemExportBytes(
      ChemicalExportFormat format, RSChemElement rsChemElement) {
    byte[] image = chemistryProvider.exportToImage(rsChemElement.getChemElements(), format);
    rsChemElement.setDataImage(image);
    return rsChemElement;
  }

  @Override
  public List<String> getSupportedTypes() {
    return chemistryProvider.getSupportedFileTypes();
  }

  private Field getFieldAndAssertAuthorised(long fieldId, User subject, String authFailureMsg) {
    Field field = fieldDao.get(fieldId);
    if (!permUtils.isPermitted(field.getStructuredDocument(), PermissionType.WRITE, subject)) {
      throw new AuthorizationException(
          authMsgGenerator.getFailedMessage(subject.getUsername(), authFailureMsg));
    }
    return field;
  }

  /* ====================
   *  for testing
   * ==================== */

  public void setRecordAdapter(BaseRecordAdaptable recordAdapter) {
    this.recordAdapter = recordAdapter;
  }

  public void setRsChemElementDao(RSChemElementDao rsChemElementDao) {
    this.rsChemElementDao = rsChemElementDao;
  }

  public void setChemistryFileManager(EcatChemistryFileManager chemFileManager) {
    this.chemistryFileManager = chemFileManager;
  }

  public void setFieldManager(FieldManager fieldManager) {
    this.fieldManager = fieldManager;
  }

  public void setPermissionUtils(IPermissionUtils permissionUtils) {
    this.permissionUtils = permissionUtils;
  }

  public void setFieldParser(FieldParser fieldParser) {
    this.fieldParser = fieldParser;
  }

  public void setChemistryProvider(ChemistryProvider chemistryProvider) {
    this.chemistryProvider = chemistryProvider;
  }
}
