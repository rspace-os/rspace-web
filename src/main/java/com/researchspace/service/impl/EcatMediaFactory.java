package com.researchspace.service.impl;

import static com.researchspace.core.util.MediaUtils.getIconPathForSuffix;
import static com.researchspace.core.util.imageutils.ImageUtils.getBufferedImageFromInputImageStream;
import static com.researchspace.model.EcatImage.MAX_PAGE_DISPLAY_WIDTH;
import static org.apache.commons.io.FilenameUtils.getExtension;

import com.researchspace.core.util.MediaUtils;
import com.researchspace.core.util.imageutils.ImageUtils;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.*;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.record.DOCUMENT_CATEGORIES;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.ImportOverride;
import com.researchspace.service.IMediaFactory;
import com.researchspace.service.chemistry.ChemistryProvider;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/** It is used to generate and manipulate anything related with EcatMediaFile and its subclasses */
public class EcatMediaFactory implements IMediaFactory {

  public Long getMaxImageMemorySize() {
    return maxImageMemorySize;
  }

  public static final String DEFAULT_GALLERY_ICON_PNG = "unknownDocument.png";

  private static final String TIFF_NO_PREVIEW_PNG = "tiffNoPreview.png";

  @Autowired private ResourceLoader resourceLoader;

  @Autowired private ChemistryProvider chemistryProvider;

  private @Autowired @Qualifier("compositeFileStore") FileStore fileStore;

  public void setFileStore(FileStore fileStore) {
    this.fileStore = fileStore;
  }

  public void setResourceLoader(ResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
  }

  public static final int SIZE = 1024;

  private Long maxImageMemorySize = EcatImage.MAX_IMAGE_IN_MEMORY;

  private Logger log = LoggerFactory.getLogger(EcatMediaFactory.class);

  /**
   * Setter in bytes for the the maximum size of image to be scaled in one go.
   *
   * @param maxImageMemorySize
   */
  public void setMaxImageMemorySize(Long maxImageMemorySize) {
    this.maxImageMemorySize = maxImageMemorySize;
  }

  // RA re rspac2191 we create ImageBlobs the same as before then calling code converts to
  // FileProperties
  // as this code is complicated enough already
  @Override
  public EcatImage generateEcatImage(
      User user,
      FileProperty fProp,
      File imageFile,
      String extension,
      String filename,
      ImportOverride override)
      throws IOException {

    Validate.notNull(imageFile, "image file can't be null");
    EcatImage ecatImage = createEcatImageWithCoreProperties(user, extension, filename, override);
    updateEcatImageWithUploadedFileDetails(ecatImage, imageFile, fProp, extension);
    return ecatImage;
  }

  // RA re rspac2191 we create ImageBlobs the same as before then calling code converts to
  // FileProperties
  // as this code is complicated enough already
  @Override
  public void updateEcatImageWithUploadedFileDetails(
      EcatImage ecatImage, File imageFile, FileProperty originalImgFProp, String extension)
      throws IOException {

    try {
      ecatImage.setFileName(originalImgFProp.getFileName());
      ecatImage.setFileProperty(originalImgFProp);
      Long fileSize = Long.parseLong(originalImgFProp.getFileSize());
      ecatImage.setSize(fileSize);
      Dimension size = getDimension(imageFile);
      ecatImage.setWidth(size.width);
      ecatImage.setHeight(size.height);
      try (FileInputStream inputStream = new FileInputStream(imageFile)) {
        if (shouldCreateWorkingCopy(size)) {
          generateWorkingImageAndThumbnail(extension, fileSize, ecatImage, inputStream);
        } else if (isTiffImage(extension)) { // small tiff
          convertTiffFile(imageFile, ecatImage, size);
        } else { // small, non-tiff other image that doesn't need resizing
          handleSmallNonTiffimage(extension, ecatImage, size, inputStream);
        }
      }
    } catch (ImageReadException e) {
      log.error("Could't read image file {}", imageFile.getName());
    }
  }

  private Dimension getDimension(File imageFile) throws IOException, ImageReadException {
    try (FileInputStream fis = new FileInputStream(imageFile)) {
      // Try to get the image dimensions using the thumbnailator package to maintain
      // image metadata and save correct height/width to ecatImage.
      BufferedImage bufferedImage = Thumbnails.of(fis).scale(1).asBufferedImage();
      return new Dimension(bufferedImage.getWidth(), bufferedImage.getHeight());
    } catch (Exception e) {
      // If we fail to get dimensions using thumbnailator package, try with
      // apache imaging package instead.
      log.error("Error getting dimensions for image: {}", imageFile.getName(), e);
      return Imaging.getImageSize(imageFile);
    }
  }

  private EcatImage createEcatImageWithCoreProperties(
      User user, String extension, String filename, ImportOverride override) {
    EcatImage ecatImage = override == null ? new EcatImage() : new EcatImage(override);
    ecatImage.setOwner(user);
    ecatImage.setCreatedBy(user.getUsername());
    ecatImage.setModifiedBy(user.getUsername());
    ecatImage.setName(filename);
    ecatImage.setCreatedBy(user.getUsername());
    ecatImage.addType(RecordType.MEDIA_FILE);
    ecatImage.setExtension(extension);
    ecatImage.setContentType(MediaUtils.getContentTypeForFileExtension(extension));
    return ecatImage;
  }

  private void handleSmallNonTiffimage(
      String extension, EcatImage ecatImage, Dimension size, FileInputStream inputStream)
      throws IOException {
    log.debug("small nontiff, just  thumbail needed,", size.width);
    Optional<BufferedImage> buffImageOpt = getBufferedImageFromInputImageStream(inputStream);
    if (buffImageOpt.isPresent()) {
      ecatImage.setWorkingImage(null);
      ecatImage.setWidthResized(size.width);
      ecatImage.setHeightResized(size.height);
      createAndSetThumbnail(buffImageOpt.get(), ecatImage);
    } else {
      getImageDefaultIcon(extension, ecatImage);
    }
  }

  private void convertTiffFile(File tempImageFile, EcatImage ecatImage, Dimension size)
      throws IOException {
    log.debug("small tiff width is {}, creating thumbnail", size.width);

    Optional<BufferedImage> buffImageOpt = ImageUtils.getBufferedImageFromTiffFile(tempImageFile);
    if (buffImageOpt.isPresent()) {
      try (ByteArrayOutputStream baos = new ByteArrayOutputStream(SIZE)) {
        ImageIO.write(buffImageOpt.get(), "png", baos);
        baos.flush();
        ecatImage.setWorkingImage(new ImageBlob(baos.toByteArray()));
        ecatImage.setWidthResized(size.width);
        ecatImage.setHeightResized(size.height);
      }
      createAndSetThumbnail(buffImageOpt.get(), ecatImage);
    } else {
      log.warn(
          "Couldn't read TIFF file  ({}-{}), using icon",
          ecatImage.getFileName(),
          ecatImage.getId());
      BufferedImage tiffIcon = getResourceImage("tiff.png");
      createAndSetWorkingImageAndThumbnail(ecatImage, tiffIcon);
    }
  }

  private void generateWorkingImageAndThumbnail(
      String extension, Long fileSize, EcatImage ecatImage, FileInputStream inputStream)
      throws IOException {

    // if we're not too big, we scale normally - this gives better results
    // for images that are only a bit larger than page size
    if (fileSize < maxImageMemorySize) {
      log.debug(
          "file size is {}, doing normal working image, as is < maxSize {}",
          fileSize,
          maxImageMemorySize);
      Optional<BufferedImage> resizedImage =
          ImageUtils.scale(inputStream, EcatImage.MAX_PAGE_DISPLAY_WIDTH, extension);
      if (resizedImage.isPresent()) {
        createAndSetWorkingImageAndThumbnail(ecatImage, resizedImage.get());
      } else {
        getImageDefaultIcon(extension, ecatImage);
      }
      // for large non-tiffs,s cale by sampling
    } else if (!isTiffImage(extension) && fileSize >= maxImageMemorySize) {
      log.debug("file size is {} and not tiff, sampling", fileSize);
      BufferedImage resizedImage =
          ImageUtils.scaleWithThumbnailator(inputStream, MAX_PAGE_DISPLAY_WIDTH);
      createAndSetWorkingImageAndThumbnail(ecatImage, resizedImage);
    } else {
      // we're a very large tiff file, that we don't want to resize in
      // case of OOM errors.
      // so we just use a placeholder
      log.debug("large tiff ({}), using icon", fileSize);
      BufferedImage tiffIcon = getResourceImage(TIFF_NO_PREVIEW_PNG);
      createAndSetWorkingImageAndThumbnail(ecatImage, tiffIcon);
    }
  }

  private boolean isTiffImage(String extension) {
    return ImageUtils.isTiff(extension);
  }

  private boolean shouldCreateWorkingCopy(Dimension size) {
    return size.width > MAX_PAGE_DISPLAY_WIDTH;
  }

  private BufferedImage getImageDefaultIcon(String extension, EcatImage ecatImage)
      throws IOException {
    log.warn(
        "Couldn't  interpret image file  ({}-{}), using icon",
        ecatImage.getFileName(),
        ecatImage.getId());
    BufferedImage tiffIcon = getResourceImage(extension + ".png");
    createAndSetWorkingImageAndThumbnail(ecatImage, tiffIcon);
    return tiffIcon;
  }

  private BufferedImage getResourceImage(String iconName) throws IOException {
    Resource resource = resourceLoader.getResource(getIconPathForSuffix(iconName));
    if (resource == null || !resource.exists()) {
      resource = resourceLoader.getResource(getIconPathForSuffix(DEFAULT_GALLERY_ICON_PNG));
    }

    return ImageIO.read(resource.getInputStream());
  }

  private void createAndSetWorkingImageAndThumbnail(EcatImage ecatImage, BufferedImage resizedImage)
      throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(SIZE);
    ImageIO.write(resizedImage, "png", baos);
    baos.flush();
    ecatImage.setWorkingImage(new ImageBlob(baos.toByteArray()));
    baos.close();
    ecatImage.setWidthResized(resizedImage.getWidth());
    ecatImage.setHeightResized(resizedImage.getHeight());
    createAndSetThumbnail(resizedImage, ecatImage);
  }

  private void createAndSetThumbnail(BufferedImage bufferedImage, EcatImage ecatImage)
      throws IOException {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream(SIZE); ) {
      ImageUtils.createThumbnail(
          bufferedImage,
          ImageUtils.DEFAULT_THUMBNAIL_DIMNSN,
          ImageUtils.DEFAULT_THUMBNAIL_DIMNSN,
          baos,
          "png");
      ecatImage.setImageThumbnailed(new ImageBlob(baos.toByteArray()));
    }
  }

  @Override
  public EcatChemistryFile generateEcatChemistryFile(
      User user, FileProperty fprop, String extensionType, String fileName, ImportOverride override)
      throws IOException {
    EcatChemistryFile ecatChemistryFile =
        override == null ? new EcatChemistryFile() : new EcatChemistryFile(override);
    String chemString = chemistryProvider.convert(fileStore.findFile(fprop));
    ecatChemistryFile.setChemString(chemString);
    ecatChemistryFile.setOwner(user);
    ecatChemistryFile.setFileName(fileName);
    ecatChemistryFile.setFileProperty(fprop);
    ecatChemistryFile.setSize(Long.parseLong(fprop.getFileSize()));

    String mimeType = URLConnection.guessContentTypeFromName(fileName);
    if (mimeType == null) {
      mimeType = MediaUtils.getContentTypeForFileExtension(extensionType);
    }
    ecatChemistryFile.setContentType(mimeType);
    ecatChemistryFile.setCreatedBy(user.getUsername());
    ecatChemistryFile.setModifiedBy(user.getUsername());
    ecatChemistryFile.setName(fileName);
    ecatChemistryFile.addType(RecordType.MEDIA_FILE);
    String[] parts = fileName.split("\\.");
    ecatChemistryFile.setExtension(parts[parts.length - 1]);
    return ecatChemistryFile;
  }

  @Override
  public EcatVideo generateEcatVideo(
      User user,
      FileProperty fprop,
      String extensionType,
      String filename,
      ImportOverride override) {
    EcatVideo ecatVideo = override == null ? new EcatVideo() : new EcatVideo(override);
    configureAV(user, fprop, extensionType, filename, ecatVideo);
    return ecatVideo;
  }

  @Override
  public EcatAudio generateEcatAudio(
      User user,
      FileProperty fprop,
      String extensionType,
      String filename,
      ImportOverride override) {
    EcatAudio ecatAudio = override == null ? new EcatAudio() : new EcatAudio(override);
    configureAV(user, fprop, extensionType, filename, ecatAudio);
    return ecatAudio;
  }

  private void configureAV(
      User user, FileProperty fprop, String extensionType, String filename, EcatMediaFile ecatAV) {
    ecatAV.setOwner(user);
    ecatAV.setFileName(fprop.getFileName());
    ecatAV.setFileProperty(fprop);
    ecatAV.setSize(Long.parseLong(fprop.getFileSize()));
    ecatAV.setCreatedBy(user.getUsername());
    ecatAV.setModifiedBy(user.getUsername());
    ecatAV.setName(filename);
    ecatAV.addType(RecordType.MEDIA_FILE);

    String mimeType = URLConnection.guessContentTypeFromName(filename);
    if (mimeType == null) {
      mimeType = MediaUtils.getContentTypeForFileExtension(extensionType);
    }
    ecatAV.setContentType(mimeType);
    String[] parts = filename.split("\\.");
    ecatAV.setExtension(parts[parts.length - 1]);
  }

  @Override
  public EcatDocumentFile generateEcatDocument(
      User user,
      FileProperty fprop,
      String extensionType,
      String documentType,
      String fileName,
      ImportOverride override) {
    EcatDocumentFile documentFile =
        override == null ? new EcatDocumentFile() : new EcatDocumentFile(override);
    documentFile.setOwner(user);
    documentFile.setFileName(fprop.getFileName());

    if (documentType.equals(MediaUtils.DOCUMENT_MEDIA_FLDER_NAME)) {
      documentFile.setDocumentType(DOCUMENT_CATEGORIES.DOCUMENTFILE);
    } else if (documentType.equals(Folder.EXPORTS_FOLDER_NAME)) {
      documentFile.setDocumentType(DOCUMENT_CATEGORIES.EXPORTEDFILE);
    } else if (MediaUtils.DMP_MEDIA_FLDER_NAME.equals(documentType)) {
      documentFile.setDocumentType(MediaUtils.DMP_MEDIA_FLDER_NAME);
    } else {
      documentFile.setDocumentType(DOCUMENT_CATEGORIES.MISCFILE);
    }
    documentFile.setFileProperty(fprop);
    documentFile.setSize(Long.parseLong(fprop.getFileSize()));

    String mimeType = URLConnection.guessContentTypeFromName(fileName);
    if (mimeType == null) {
      mimeType = MediaUtils.getContentTypeForFileExtension(extensionType);
    }
    documentFile.setContentType(mimeType);
    documentFile.setCreatedBy(user.getUsername());
    documentFile.setModifiedBy(user.getUsername());
    documentFile.setName(fileName);
    documentFile.setCreatedBy(user.getUsername());
    documentFile.addType(RecordType.MEDIA_FILE);
    String[] parts = fileName.split("\\.");
    documentFile.setExtension(parts[parts.length - 1]);
    return documentFile;
  }

  @Override
  public byte[] getFileSuffixIcon(String filename) throws IOException {
    Resource resource =
        resourceLoader.getResource(getIconPathForSuffix(getExtension(filename) + ".png"));
    if (resource == null || !resource.exists()) {
      resource =
          resourceLoader.getResource(
              getIconPathForSuffix(EcatMediaFactory.DEFAULT_GALLERY_ICON_PNG));
    }
    byte[] bytes = null;
    try (InputStream is = resource.getInputStream()) {
      if (is != null) {
        bytes = IOUtils.toByteArray(is);
      }
    }
    return bytes;
  }
}
