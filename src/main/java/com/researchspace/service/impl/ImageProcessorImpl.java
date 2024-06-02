package com.researchspace.service.impl;

import com.researchspace.core.util.IoUtils;
import com.researchspace.core.util.imageutils.ImageUtils;
import com.researchspace.files.service.FileStore;
import com.researchspace.files.service.InternalFileStore;
import com.researchspace.model.EcatImage;
import com.researchspace.model.FileProperty;
import com.researchspace.model.ImageBlob;
import com.researchspace.model.User;
import com.researchspace.service.FileDuplicateStrategy;
import com.researchspace.service.ImageProcessor;
import com.researchspace.service.RecordManager;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Optional;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

@Slf4j
public class ImageProcessorImpl implements ImageProcessor {

  private @Autowired @Qualifier("compositeFileStore") FileStore fileStore;
  private @Autowired RecordManager recordManager;

  /*
   * this does not create FP images from existing ImageBlobs. If an image is stored as an image blob, its rotatios
   * will be stored in the same way.
   */
  @Override
  public Optional<EcatImage> rotate(EcatImage ecatImage, byte timesToRotate, User subject)
      throws IOException {
    BufferedImage rotatedWorkingImg = null;
    BufferedImage thumbnailSource = null;
    FileProperty sourceFileProperty = ecatImage.getFileProperty();
    if (workingImageExists(EcatImage.MAX_PAGE_DISPLAY_WIDTH, ecatImage)) {
      // Get working image
      if (ecatImage.getWorkingImageFP() != null) {
        Optional<BufferedImage> rotatedWorking =
            rotateFileStoreImage(timesToRotate, ecatImage, ecatImage.getWorkingImageFP());
        if (rotatedWorking.isPresent()) {
          thumbnailSource = rotatedWorking.get();
        }
      } else {
        BufferedImage workingBI = readWorkingImage(ecatImage);
        rotatedWorkingImg = rotateImage(timesToRotate, workingBI);
        ecatImage = updateWorkingImageBlob(ecatImage, rotatedWorkingImg);
        ecatImage.setWidthResized(rotatedWorkingImg.getWidth());
        ecatImage.setHeightResized(rotatedWorkingImg.getHeight());
        thumbnailSource = rotatedWorkingImg;
      }
    }
    // rotate original file as well
    Optional<BufferedImage> rotatedOriginalImgOpt;
    if (!ImageUtils.isTiff(ecatImage.getExtension())) {
      rotatedOriginalImgOpt = rotateFileStoreImage(timesToRotate, ecatImage, sourceFileProperty);
      if (rotatedOriginalImgOpt.isPresent()) {
        thumbnailSource = rotatedOriginalImgOpt.get();
      }
    } else {
      rotatedOriginalImgOpt = rotateOriginalTiffFile(timesToRotate, ecatImage, sourceFileProperty);
    }
    if (!rotatedOriginalImgOpt.isPresent()) {
      log.error(
          "Could not rotate the original image for FileProperty id {}; aborting",
          sourceFileProperty.getId() == null ? "" : sourceFileProperty.getId());
      return Optional.empty();
    }
    // thumbnail source for rotation is either the working copy ( for large or
    // tiffs)
    // or for orginal file (for small files where we don't have a working copy)
    if (thumbnailSource == null) {
      log.error(
          "Thumbnail source is null! for for FileProperty id image {}", sourceFileProperty.getId());
      return Optional.empty();
    }
    BufferedImage rotatedOriginalImg = rotatedOriginalImgOpt.get();
    if (ecatImage.getThumbnailImageFP() != null) {
      // we can just rotate the FP thumbnail in place.
      rotateFileStoreImage(timesToRotate, ecatImage, ecatImage.getThumbnailImageFP());
    } else {
      // use working image to rotate the thumbnail, as the working image is a png file
      ecatImage = updateThumbnailImage(ecatImage, thumbnailSource);
    }
    log.info(
        "Updating dimensions from {}x{} to  {}x{}",
        ecatImage.getWidth(),
        ecatImage.getHeight(),
        rotatedOriginalImg.getWidth(),
        rotatedOriginalImg.getHeight());
    // update with new dimensions
    ecatImage.setWidth(rotatedOriginalImg.getWidth());
    ecatImage.setHeight(rotatedOriginalImg.getHeight());
    ecatImage.rotate(timesToRotate);

    return Optional.of((EcatImage) recordManager.save(ecatImage, subject));
  }

  BufferedImage readWorkingImage(EcatImage ecatImage) throws IOException {
    byte[] workingImage = ecatImage.getWorkingImage().getData();
    InputStream in = new ByteArrayInputStream(workingImage);
    return ImageIO.read(in);
  }

  Optional<BufferedImage> rotateOriginalTiffFile(
      Byte timesToRotate, EcatImage ecatImage, FileProperty sourceFileProperty) throws IOException {
    File tiff = fileStore.findFile(sourceFileProperty);
    File secureTmpDir = IoUtils.createOrGetSecureTempDirectory().toFile();
    File tempFile = File.createTempFile("tiffRotate", ".tif", secureTmpDir);
    boolean rotated = rotateTiff(timesToRotate, tiff, tempFile);
    if (!rotated) {
      return Optional.empty();
    }
    fileStore.save(sourceFileProperty, tempFile, FileDuplicateStrategy.REPLACE);
    return ImageUtils.getBufferedImageFromTiffFile(tiff);
  }

  private boolean workingImageExists(final int maxDisplayWidth, EcatImage ecatImage) {
    return ImageUtils.isTiff(ecatImage.getExtension()) || ecatImage.getWidth() > maxDisplayWidth;
  }

  // package scoped for testing
  BufferedImage rotateImage(byte timesToRotate, BufferedImage workingBI) {
    return ImageUtils.rotateImage(workingBI, (Math.PI / 2) * timesToRotate);
  }

  boolean rotateTiff(Byte timesToRotate, File tiff, File tempFile) {
    return ImageUtils.rotateTiff(tiff, timesToRotate.intValue(), tempFile);
  }

  // package scoped for testing and overriding in subclass
  Optional<BufferedImage> rotateFileStoreImage(
      byte timesToRotate, EcatImage ecatImage, FileProperty sourceFileProperty) throws IOException {
    BufferedImage rotated = null;
    Optional<FileInputStream> fisOpt = fileStore.retrieve(sourceFileProperty);
    if (fisOpt.isPresent()) {
      try (FileInputStream fis = fisOpt.get()) {
        Optional<BufferedImage> original =
            ImageUtils.getBufferedImageFromUploadedFile(ecatImage.getExtension(), fis);
        if (!original.isPresent()) {
          log.warn("Image {} could not be interpreted for rotation", ecatImage.getId());
          return Optional.empty();
        }
        rotated = ImageUtils.rotateImage(original.get(), (Math.PI / 2) * timesToRotate);
        File secureTmpDir = IoUtils.createOrGetSecureTempDirectory().toFile();
        File tempOutputfile =
            File.createTempFile("imgRotate", "." + ecatImage.getExtension(), secureTmpDir);
        ImageIO.write(rotated, ecatImage.getExtension(), tempOutputfile);
        fileStore.save(sourceFileProperty, tempOutputfile, FileDuplicateStrategy.REPLACE);
      }
    } else {
      log.error("Could not retrieve image file to rotate");
    }
    return Optional.ofNullable(rotated);
  }

  // rspac-2191. The temporary ImageBlob created in EcatMediaFactory is not
  // persisted, but converted to FileProperty, for new images
  public void transformImageBlobToFileProperty(String originalFileName, User user, EcatImage image)
      throws IOException {
    //
    ImageBlob thumbnail = image.getImageThumbnailed();
    ImageBlob working = image.getWorkingImage();
    // this is needed so that hibernate does not throw transient object exception trying to save the
    // ImageBlob
    // which here we are using a Transient object
    image.setImageThumbnailed(null);
    image.setWorkingImage(null);
    if (thumbnail != null) {
      try (ByteArrayInputStream bais = new ByteArrayInputStream(thumbnail.getData())) {
        FileProperty fpThumbnail =
            fileStore.createAndSaveFileProperty(
                InternalFileStore.IMG_THUMBNAIL_CATEGORY, user, originalFileName, bais);
        image.setThumbnailImageFP(fpThumbnail);
      }
    }
    if (working != null) {
      try (ByteArrayInputStream bais = new ByteArrayInputStream(working.getData())) {
        FileProperty fpWorking =
            fileStore.createAndSaveFileProperty(
                InternalFileStore.IMG_WORKING_CATEGORY, user, originalFileName, bais);
        image.setWorkingImageFP(fpWorking);
      }
    }
  }

  private EcatImage updateWorkingImageBlob(EcatImage ecatImage, BufferedImage bufferedImage) {

    try (ByteArrayOutputStream baos = new ByteArrayOutputStream(EcatMediaFactory.SIZE)) {
      ImageIO.write(bufferedImage, "png", baos);
      baos.flush();
      ecatImage.getWorkingImage().setData(baos.toByteArray());
    } catch (IOException e) {
      log.error("Error while writing blob for image with id: {}", ecatImage.getId(), e);
    }
    return ecatImage;
  }

  private EcatImage updateThumbnailImage(EcatImage ecatImage, BufferedImage bufferedImage) {
    final int sizearg = 96;

    try (ByteArrayOutputStream baos = new ByteArrayOutputStream(EcatMediaFactory.SIZE); ) {
      Thumbnails.of(bufferedImage).size(sizearg, sizearg).outputFormat("png").toOutputStream(baos);
      ecatImage.setImageThumbnailed(new ImageBlob(baos.toByteArray()));
    } catch (IOException e) {
      log.error("Error while writing thumbnail for image with id: {}.", ecatImage.getId(), e);
    }
    ecatImage.setModificationDate(new Date());
    return ecatImage;
  }
}
