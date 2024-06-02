package com.researchspace.service.impl;

import static com.researchspace.core.util.MediaUtils.AUDIO_MEDIA_FLDER_NAME;
import static com.researchspace.core.util.MediaUtils.CHEMISTRY_MEDIA_FLDER_NAME;
import static com.researchspace.core.util.MediaUtils.IMAGES_MEDIA_FLDER_NAME;
import static com.researchspace.core.util.MediaUtils.VIDEO_MEDIA_FLDER_NAME;
import static com.researchspace.core.util.MediaUtils.extractFileTypeFromPath;
import static com.researchspace.core.util.MediaUtils.getExtension;
import static com.researchspace.model.record.Folder.targetFolderIsCorrectTypeForMedia;
import static org.apache.commons.lang.StringUtils.isBlank;

import com.axiope.search.IFileIndexer;
import com.researchspace.core.util.IoUtils;
import com.researchspace.core.util.MediaUtils;
import com.researchspace.core.util.imageutils.ImageUtils;
import com.researchspace.dao.EcatImageDao;
import com.researchspace.dao.FieldDao;
import com.researchspace.dao.RSMathDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.ChemElementsFormat;
import com.researchspace.model.EcatAudio;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.EcatComment;
import com.researchspace.model.EcatCommentItem;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatImageAnnotation;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.EcatVideo;
import com.researchspace.model.FileProperty;
import com.researchspace.model.ImageBlob;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.RSMath;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.field.Field;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.BaseRecordAdaptable;
import com.researchspace.model.record.DeltaType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.ImportOverride;
import com.researchspace.model.record.LinkedFieldsToMediaRecordInitPolicy;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.service.EcatCommentManager;
import com.researchspace.service.EcatImageAnnotationManager;
import com.researchspace.service.FieldManager;
import com.researchspace.service.FolderManager;
import com.researchspace.service.IMediaFactory;
import com.researchspace.service.ImageProcessor;
import com.researchspace.service.MediaFileLockHandler;
import com.researchspace.service.MediaManager;
import com.researchspace.service.OperationFailedMessageGenerator;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.RecordManager;
import com.researchspace.service.ThumbnailManager;
import com.researchspace.service.chemistry.ChemistryProvider;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service("mediaManager")
public class MediaManagerImpl implements MediaManager {

  Logger log = LoggerFactory.getLogger(MediaManagerImpl.class);

  private @Autowired RecordManager recordManager;
  private @Autowired FolderManager folderManager;
  private @Autowired EcatCommentManager commentManager;
  private @Autowired RSChemElementManager rsChemElementManager;
  private @Autowired EcatImageAnnotationManager ecatImageAnnotationManager;
  private @Autowired ChemistryProvider chemistryProvider;
  private @Autowired ThumbnailManager thumbnailMgr;
  private @Autowired ImageProcessor imageProcessor;
  private @Autowired IFileIndexer fileIndexer;
  private @Autowired OperationFailedMessageGenerator authMsgGenerator;

  private @Autowired IPermissionUtils permUtils;
  private @Autowired BaseRecordAdaptable recordAdapter;
  private @Autowired IMediaFactory mediaFactory;
  private @Autowired FieldManager fieldMgr;

  private @Autowired EcatImageDao ecatImageDao;
  private @Autowired FieldDao fieldDao;
  private @Autowired RSMathDao mathDao;
  private @Autowired RecordDao recordDao;

  private @Autowired BaseRecordAdaptable baseRecordAdapter;

  private final MediaFileLockHandler lockHandler = new MediaFileLockHandler();

  private @Autowired @Qualifier("compositeFileStore") FileStore fileStore;

  public void setFileStore(FileStore fileStore) {
    this.fileStore = fileStore;
  }

  @Override
  public EcatImage saveNewImage(
      String originalFileName, InputStream inputStream, User user, Folder targetFolder)
      throws IOException {
    return saveNewImage(originalFileName, inputStream, user, targetFolder, null);
  }

  @Override
  public EcatImage saveNewImage(
      String originalFileName,
      InputStream inputStream,
      User user,
      Folder targetFolder,
      ImportOverride override)
      throws IOException {
    return (EcatImage)
        saveMediaFile(
            inputStream,
            null,
            originalFileName,
            originalFileName,
            null,
            targetFolder,
            null,
            user,
            override);
  }

  @Override
  public EcatImage saveEditedImage(EcatImage sourceImage, String uiBase64Image, User user)
      throws IOException {

    String newExtension = ImageUtils.getExtensionFromBase64DataImage(uiBase64Image);
    String newName =
        FilenameUtils.removeExtension(sourceImage.getName()) + "_edited." + newExtension;

    byte[] decodedBytes = ImageUtils.getImageBytesFromBase64DataImage(uiBase64Image);
    ByteArrayInputStream bais = new ByteArrayInputStream(decodedBytes);

    EcatImage newImage = saveNewImage(newName, bais, user, sourceImage.getParent());

    // set reference to originally uploaded image version
    if (sourceImage.getOriginalImage() != null) {
      newImage.setOriginalImage(sourceImage.getOriginalImage());
      newImage.setOriginalImageVersion(sourceImage.getOriginalImageVersion());
    } else {
      newImage.setOriginalImage(sourceImage);
      newImage.setOriginalImageVersion(sourceImage.getVersion());
    }
    recordDao.save(newImage);

    return newImage;
  }

  @Override
  public EcatVideo saveNewVideo(
      String originalFileName,
      InputStream inputStream,
      User user,
      Folder targetFolder,
      ImportOverride override)
      throws IOException {
    return (EcatVideo)
        saveMediaFile(
            inputStream,
            null,
            originalFileName,
            originalFileName,
            null,
            targetFolder,
            null,
            user,
            override);
  }

  @Override
  public EcatAudio saveNewAudio(
      String originalFileName,
      InputStream inputStream,
      User user,
      Folder targetFolder,
      ImportOverride override)
      throws IOException {
    return (EcatAudio)
        saveMediaFile(
            inputStream,
            null,
            originalFileName,
            originalFileName,
            null,
            targetFolder,
            null,
            user,
            override);
  }

  @Override
  public EcatDocumentFile saveNewDMP(
      String originalFileName, InputStream inputStream, User user, ImportOverride override)
      throws IOException {
    return (EcatDocumentFile)
        doSaveMediaFile(
            inputStream,
            null,
            originalFileName,
            originalFileName,
            null,
            null,
            null,
            user,
            override,
            MediaUtils.DMP_MEDIA_FLDER_NAME);
  }

  @Override
  public EcatChemistryFile saveNewChemFile(
      String originalFileName,
      InputStream inputStream,
      User user,
      Folder targetFolder,
      ImportOverride override)
      throws IOException {
    return (EcatChemistryFile)
        saveMediaFile(
            inputStream,
            null,
            originalFileName,
            originalFileName,
            null,
            targetFolder,
            null,
            user,
            override);
  }

  @Override
  public EcatDocumentFile saveNewDocument(
      String originalFileName,
      InputStream inputStream,
      User user,
      Folder targetFolder,
      ImportOverride override)
      throws IOException {
    return (EcatDocumentFile)
        saveMediaFile(
            inputStream,
            null,
            originalFileName,
            originalFileName,
            null,
            targetFolder,
            null,
            user,
            override);
  }

  @Override
  public EcatMediaFile saveMediaFile(
      InputStream inputStream,
      Long mediaFileId,
      String displayName,
      String originalFileName,
      Long fieldId,
      Folder targetFolder,
      String caption,
      User user,
      ImportOverride override)
      throws IOException {

    String mediaFolderType = extractFileTypeFromPath(originalFileName);
    return doSaveMediaFile(
        inputStream,
        mediaFileId,
        displayName,
        originalFileName,
        fieldId,
        targetFolder,
        caption,
        user,
        override,
        mediaFolderType);
  }

  private EcatMediaFile doSaveMediaFile(
      InputStream inputStream,
      Long mediaFileId,
      String displayName,
      String originalFileName,
      Long fieldId,
      Folder targetFolder,
      String caption,
      User user,
      ImportOverride override,
      String mediaFolderType)
      throws IOException {
    // are we making new revision of file
    if (mediaFileId != null) {
      return updateMediaFile(mediaFileId, inputStream, originalFileName, user, null);
    }

    log.info("Saving new media file {} into {} ", originalFileName, mediaFolderType);
    // if target folder is wrong type, we'll just put in top level folder, like we do if it;s not
    // specified
    if (targetFolder != null) {
      if (!targetFolder.hasAncestorMatchingPredicate(
          targetFolderIsCorrectTypeForMedia(mediaFolderType), true)) {
        targetFolder = recordManager.getGallerySubFolderForUser(mediaFolderType, user);
      }

    } else {
      targetFolder = recordManager.getGallerySubFolderForUser(mediaFolderType, user);
    }
    assertCanAddToFolder(targetFolder, user);

    String extension = getExtension(originalFileName);
    EcatMediaFile media = null;
    try (InputStream autoCloseableInputStream = inputStream) {
      if (mediaFolderType.equals(IMAGES_MEDIA_FLDER_NAME)) {
        File secureTmpDir = IoUtils.createOrGetSecureTempDirectory().toFile();
        File tempFile =
            File.createTempFile(
                "tmp_file_upload_" + originalFileName, "." + extension, secureTmpDir);
        try (FileOutputStream fos = new FileOutputStream(tempFile); ) {
          IOUtils.copy(inputStream, fos);
          FileProperty fp =
              fileStore.createAndSaveFileProperty(
                  mediaFolderType, user, originalFileName, new FileInputStream(tempFile));
          media =
              mediaFactory.generateEcatImage(
                  user, fp, tempFile, extension, originalFileName, override);
          imageProcessor.transformImageBlobToFileProperty(
              originalFileName, user, (EcatImage) media);
        }
      } else {
        FileProperty fp =
            fileStore.createAndSaveFileProperty(
                mediaFolderType, user, originalFileName, inputStream);
        if (mediaFolderType.equals(VIDEO_MEDIA_FLDER_NAME)) {
          media = mediaFactory.generateEcatVideo(user, fp, extension, originalFileName, override);
        } else if (mediaFolderType.equals(AUDIO_MEDIA_FLDER_NAME)) {
          media = mediaFactory.generateEcatAudio(user, fp, extension, originalFileName, override);
        } else if (mediaFolderType.equals(CHEMISTRY_MEDIA_FLDER_NAME)) {
          media =
              mediaFactory.generateEcatChemistryFile(
                  user, fp, extension, originalFileName, override);
        } else {
          // this will also include DMPs, which are always added to the top-level folder.
          media =
              mediaFactory.generateEcatDocument(
                  user, fp, extension, mediaFolderType, originalFileName, override);
        }
      }
    }
    if (media == null) {
      throw new IllegalStateException(
          "Media file could not be saved - " + originalFileName.split(Pattern.quote("."))[0]);
    }

    folderManager.addChild(targetFolder.getId(), media, user);

    if (media.isEcatDocument() && !media.getFileProperty().isExternal()) {
      indexFile(media);
    }
    // if uploading into a field, we can associate uploaded file with field here
    if (fieldId != null) {
      // this will check permissions
      fieldMgr.addMediaFileLink(media.getId(), user, fieldId, false);
    }
    media.setName(displayName);
    if (!isBlank(caption)) {
      media.setDescription(caption);
    }
    recordDao.save(media); // ensure name/description are set
    return media;
  }

  @Override
  public EcatMediaFile saveMediaFile(
      InputStream inputStream,
      Long mediaFileId,
      String displayName,
      String originalFileName,
      Long fieldId,
      Folder targetFolder,
      String caption,
      User user)
      throws IOException {
    return saveMediaFile(
        inputStream,
        mediaFileId,
        displayName,
        originalFileName,
        fieldId,
        targetFolder,
        caption,
        user,
        null);
  }

  @Override
  public EcatMediaFile updateMediaFile(
      Long mediaFileId, InputStream inputStream, String updatedFileName, User user, String lockId)
      throws IOException {

    BaseRecord recToUpdate =
        recordManager.getRecordWithLazyLoadedProperties(
            mediaFileId, user, new LinkedFieldsToMediaRecordInitPolicy(), true);

    if (!recToUpdate.isMediaRecord()) {
      throw new IllegalArgumentException(mediaFileId + " is not a media record");
    }

    String currentLock = lockHandler.getLock(recToUpdate.getGlobalIdentifier());
    if (!StringUtils.isBlank(currentLock) && !currentLock.equals(lockId)) {
      throw new IllegalStateException("The file is currently locked and can't be updated");
    }

    EcatMediaFile media = (EcatMediaFile) recToUpdate;
    assertMediaFilePermission(user, media, PermissionType.WRITE);

    String oldExtension = media.getExtension();
    String newExtension = getExtension(updatedFileName);
    if (!isNewFileExtensionAllowed(oldExtension, newExtension)) {
      throw new IllegalArgumentException(
          "Cannot update ." + oldExtension + " file with ." + newExtension);
    }

    log.debug("Updating existing media file {}", mediaFileId);
    String fileType = extractFileTypeFromPath(updatedFileName);

    FileProperty newFileProperty;
    if (media.isImage()) {
      // delete pre-exitsting thumbnails before we start generating new ones
      thumbnailMgr.deleteImageThumbnails((EcatImage) media, user);
      File secureTmpDir = IoUtils.createOrGetSecureTempDirectory().toFile();
      File tempFile =
          File.createTempFile("originalFileName", "." + media.getExtension(), secureTmpDir);
      try (FileOutputStream fos = new FileOutputStream(tempFile); ) {
        IOUtils.copy(inputStream, fos);

        newFileProperty =
            fileStore.createAndSaveFileProperty(
                fileType, user, updatedFileName, new FileInputStream(tempFile));
        EcatImage mediaAsImage = (EcatImage) media;
        mediaFactory.updateEcatImageWithUploadedFileDetails(
            mediaAsImage, tempFile, newFileProperty, media.getExtension());
        imageProcessor.transformImageBlobToFileProperty(
            mediaAsImage.getFileName(), user, mediaAsImage);
      }
    } else {
      newFileProperty =
          fileStore.createAndSaveFileProperty(fileType, user, updatedFileName, inputStream);
    }

    if (media.isEcatDocument()) {
      indexFile(media);
      // reset the thumbnail to force re-generation on next request
      ((EcatDocumentFile) media).setThumbNail(null);
    }

    // Update the RSChemElements associated with this chemistry file
    if (media.isChemistryFile()) {
      rsChemElementManager.updateAssociatedChemElements(
          (EcatChemistryFile) media, newFileProperty, user);
    }

    media.setName(updatedFileName);
    media.setFileProperty(newFileProperty);
    media.setSize(Long.parseLong(newFileProperty.getFileSize()));
    media.setFileName(newFileProperty.getFileName());
    media.setModificationDate(new Date());
    media.setModifiedBy(user.getUsername());
    media.setExtension(newExtension);
    media.setVersion(media.getVersion() + 1);
    recordDao.save(media);

    updateRevisionOfLinkedDocuments(media, user);

    return media;
  }

  private boolean isNewFileExtensionAllowed(String oldExtension, String newExtension) {
    String oldExtensionLC = StringUtils.lowerCase(oldExtension);
    String newExtensionLC = StringUtils.lowerCase(newExtension);
    if (oldExtensionLC.equals(newExtensionLC)) {
      return true;
    }
    if ("doc".equals(oldExtensionLC) && "docx".equals(newExtensionLC)
        || "csv".equals(oldExtensionLC) && "xlsx".equals(newExtensionLC)
        || "xls".equals(oldExtensionLC) && "xlsx".equals(newExtensionLC)
        || "pps".equals(oldExtensionLC) && "pptx".equals(newExtensionLC)
        || "ppt".equals(oldExtensionLC) && "pptx".equals(newExtensionLC)) {
      return true; // RSPAC-1828
    }
    return false;
  }

  private void updateRevisionOfLinkedDocuments(EcatMediaFile media, User user) {
    // force update of connected documents
    Set<BaseRecord> connectedRecords = baseRecordAdapter.getAsBaseRecord(media, true);
    for (BaseRecord br : connectedRecords) {
      if (br.isStructuredDocument() && !br.isSigned()) {
        recordManager.forceVersionUpdate(
            br.getId(),
            DeltaType.ATTACHMENT_CHG,
            DeltaType.ATTACHMENT_CHG + "-" + media.getId(),
            user);
      }
    }
  }

  private void assertCanAddToFolder(Folder parent, User user) {
    if (!permUtils.isPermitted(parent, PermissionType.WRITE, user)) {
      throw new AuthorizationException(
          authMsgGenerator.getFailedMessage(user.getUsername(), "add to Folder"));
    }
  }

  private void indexFile(EcatMediaFile ecatDocFile) {
    try {
      URI uri = new URI(ecatDocFile.getFileUri());
      File iFile = new File(uri);
      if (fileIndexer.accept(iFile)) {
        fileIndexer.indexFile(iFile);
      }
    } catch (URISyntaxException | IOException e) {
      log.error("Exception on indexing ecatDocFile " + ecatDocFile.getId(), e);
    }
  }

  private Field getFieldAndAssertAuthorised(long fieldId, User subject, String authFailureMsg) {
    Field field = fieldDao.get(fieldId);
    if (!permUtils.isPermitted(field.getStructuredDocument(), PermissionType.WRITE, subject)) {
      throw new AuthorizationException(
          authMsgGenerator.getFailedMessage(subject.getUsername(), authFailureMsg));
    }
    return field;
  }

  @Override
  public RSMath saveMath(String svg, long fieldId, String latex, Long mathId, User subject) {
    Field field = getFieldAndAssertAuthorised(fieldId, subject, "save maths equation");
    RSMath math = null;
    byte[] svgBytes = svg.getBytes(StandardCharsets.UTF_8);
    ImageBlob svgByteBlob = new ImageBlob(svgBytes);
    if (mathId != null) {
      math = mathDao.get(mathId);
      math.setLatex(latex);
      math.setMathSvg(svgByteBlob);
    } else {
      math = new RSMath(svgBytes, latex, field);
      math.setRecord(field.getStructuredDocument());
    }
    return mathDao.save(math);
  }

  @Override
  public RSChemElement importChemElement(
      InputStream dataImageIS, String chem, ChemElementsFormat format, long fieldId, Record record)
      throws IOException {
    byte[] data = IOUtils.toByteArray(dataImageIS);
    String mrvString = chemistryProvider.convert(chem);
    RSChemElement rsChemElement =
        RSChemElement.builder()
            .dataImage(data)
            .chemElements(mrvString)
            .chemElementsFormat(format)
            .parentId(fieldId)
            .record(record)
            .build();
    return rsChemElementManager.save(rsChemElement, null);
  }

  @Override
  public RSMath importMathElement(
      InputStream inputStream, String latex, long fieldId, Record record) throws IOException {
    byte[] data = IOUtils.toByteArray(inputStream);
    RSMath rsMathElement = new RSMath(data, latex, fieldDao.load(fieldId));
    rsMathElement.setRecord(record);
    mathDao.save(rsMathElement);
    inputStream.close();
    return rsMathElement;
  }

  @Override
  public EcatComment insertEcatComment(String fieldId, String comment, User user) {
    Field field = fieldDao.get(Long.valueOf(fieldId));
    EcatComment ecatComment = new EcatComment(field.getId(), field.getStructuredDocument(), user);
    assertEditPermissionOnComment(user, ecatComment);
    commentManager.addComment(ecatComment);
    EcatCommentItem item = new EcatCommentItem(ecatComment, comment, user);
    commentManager.addCommentItem(ecatComment, item);
    return ecatComment;
  }

  private void assertEditPermissionOnComment(User user, EcatComment ecatComment) {
    if (!permUtils.isPermitted(ecatComment.getRecord(), PermissionType.WRITE, user)) {
      throw new AuthorizationException(
          authMsgGenerator.getFailedMessage(user.getUsername(), "insert a comment"));
    }
  }

  @Override
  public EcatComment addEcatComment(String fieldId, String commentId, String comment, User user) {
    EcatComment ecatComment =
        commentManager.getEcatComment(Long.parseLong(commentId), Long.parseLong(fieldId), user);
    assertEditPermissionOnComment(user, ecatComment);
    EcatCommentItem item = new EcatCommentItem(ecatComment, comment, user);
    commentManager.addCommentItem(ecatComment, item);
    return ecatComment;
  }

  @Override
  public EcatImageAnnotation saveSketch(
      String annotations,
      String imageBase64,
      String sketchId,
      long fieldId,
      Record record,
      User subject)
      throws IOException {
    if (subject == null) {
      log.warn("Subject should not be null; this method is authorised");
    }

    Base64 dec = new Base64();
    byte[] decodedBytes = dec.decode(imageBase64.split(",")[1]);

    BufferedImage bufferedImage = getImageFromBytes(decodedBytes);

    EcatImageAnnotation ecatImageAnnotation = null;
    if (!sketchId.equals("")) {
      ecatImageAnnotation = ecatImageAnnotationManager.get(Long.parseLong(sketchId), subject);
    }

    if (ecatImageAnnotation != null) {
      // we modify an already existing annotation
      ecatImageAnnotation.setData(decodedBytes);
      ecatImageAnnotation.setAnnotations(annotations);
    } else {
      ecatImageAnnotation = new EcatImageAnnotation(fieldId, record, decodedBytes, annotations);
    }
    if (bufferedImage != null) {
      ecatImageAnnotation.setHeight(bufferedImage.getHeight());
      ecatImageAnnotation.setWidth(bufferedImage.getWidth());
    }

    Record parentRecord = recordManager.get(ecatImageAnnotation.getRecord().getId());
    if (subject != null && !permUtils.isPermitted(parentRecord, PermissionType.READ, subject)) {
      throw new AuthorizationException(
          authMsgGenerator.getFailedMessage(subject.getUsername(), "save sketch"));
    }
    ecatImageAnnotationManager.save(ecatImageAnnotation, subject);
    return ecatImageAnnotation;
  }

  private BufferedImage getImageFromBytes(byte[] decodedBytes) throws IOException {
    BufferedImage bufferedImage = null;
    try (ByteArrayInputStream bis = new ByteArrayInputStream(decodedBytes)) {
      bufferedImage = ImageIO.read(bis);
    }
    return bufferedImage;
  }

  @Override
  public EcatImageAnnotation importSketch(
      InputStream inputStream, String annotations, long fieldId, Record record) throws IOException {

    byte[] data = IOUtils.toByteArray(inputStream);
    EcatImageAnnotation ecatImageAnnotation =
        new EcatImageAnnotation(fieldId, record, data, annotations);
    ecatImageAnnotationManager.save(ecatImageAnnotation, null);
    inputStream.close();
    return ecatImageAnnotation;
  }

  @Override
  public EcatImageAnnotation saveImageAnnotation(
      String annotations,
      String imageBase64,
      long parentId,
      Record record,
      long imageId,
      User subject)
      throws IOException {

    Base64 dec = new Base64();
    byte[] decodedBytes = dec.decode(imageBase64.split(",")[1]);

    BufferedImage bufferedImage = getImageFromBytes(decodedBytes);

    EcatImageAnnotation ecatImageAnnotation =
        ecatImageAnnotationManager.getByParentIdAndImageId(parentId, imageId, subject);
    if (ecatImageAnnotation != null) {
      Optional<BaseRecord> brOpt = recordAdapter.getAsBaseRecord(ecatImageAnnotation);
      if (!brOpt.isPresent()
          || (subject != null
              && !permUtils.isPermitted(brOpt.get(), PermissionType.WRITE, subject))) {
        throw new AuthorizationException(
            authMsgGenerator.getFailedMessage(subject.getUsername(), "save annotation"));
      }
    }

    boolean updatingExistingAnnotation = false;
    if (ecatImageAnnotation != null) {
      updatingExistingAnnotation = true;
      ecatImageAnnotation.setData(decodedBytes);
      ecatImageAnnotation.setAnnotations(annotations);
    } else {
      ecatImageAnnotation = new EcatImageAnnotation(parentId, record, decodedBytes, annotations);
      ecatImageAnnotation.setImageId(imageId);
    }

    if (bufferedImage != null) {
      ecatImageAnnotation.setHeight(bufferedImage.getHeight());
      ecatImageAnnotation.setWidth(bufferedImage.getWidth());
    }
    ecatImageAnnotationManager.save(ecatImageAnnotation, subject);

    if (updatingExistingAnnotation) {
      // force revision AFTER we've saved the annotation - the document revision will be >
      // than the annotation revision
      //	recordManager.forceVersionUpdate(parentId, DeltaType.IMAGE_ANNOTATION, null);
    }

    return ecatImageAnnotation;
  }

  @Override
  public EcatImageAnnotation importImageAnnotation(
      InputStream inputStream, String annotations, long fieldId, Record record, long imageId)
      throws IOException {

    byte[] data = IOUtils.toByteArray(inputStream);
    EcatImageAnnotation ecatImageAnnotation =
        new EcatImageAnnotation(fieldId, record, data, annotations);
    ecatImageAnnotation.setImageId(imageId);
    ecatImageAnnotationManager.save(ecatImageAnnotation, null);
    inputStream.close();

    return ecatImageAnnotation;
  }

  @Override
  public EcatImage getImage(Long imageId, User user, boolean includeBytes) {
    EcatImage img = ecatImageDao.get(imageId);
    assertMediaFilePermission(user, img, PermissionType.READ);
    if (includeBytes) {
      if (img.getImageThumbnailed() != null) {
        img.getImageThumbnailed().getData();
      }
      if (img.getWorkingImage() != null) {
        img.getWorkingImage().getData();
      }
    }
    return img;
  }

  private void assertMediaFilePermission(User user, EcatMediaFile media, PermissionType permType) {
    if (!permUtils.isRecordAccessPermitted(user, media, permType)) {
      throw new AuthorizationException(
          authMsgGenerator.getFailedMessage(user, "access media file [" + media.getId() + "]."));
    }
  }

  @Override
  public List<RecordInformation> getIdsOfLinkedDocuments(Long mediaFileId) {
    List<RecordInformation> rc = recordDao.getInfosOfDocumentsLinkedToMediaFile(mediaFileId);
    rc.stream().forEach(info -> info.setOid(new GlobalIdentifier(GlobalIdPrefix.SD, info.getId())));
    return rc;
  }

  @Override
  public MediaFileLockHandler getLockHandler() {
    return lockHandler;
  }
}
