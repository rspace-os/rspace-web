package com.researchspace.webapp.controller;

import com.researchspace.core.util.IoUtils;
import com.researchspace.core.util.ResponseUtil;
import com.researchspace.core.util.imageutils.ImageUtils;
import com.researchspace.documentconversion.spi.ConversionResult;
import com.researchspace.documentconversion.spi.DocumentConversionService;
import com.researchspace.files.service.InternalFileStore;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatImageAnnotation;
import com.researchspace.model.FileProperty;
import com.researchspace.model.ImageBlob;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.dto.ImageInfo;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.field.Field;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.EcatDocumentThumbnailInitializationPolicy;
import com.researchspace.model.record.IconEntity;
import com.researchspace.model.record.LinkedFieldsToMediaRecordInitPolicy;
import com.researchspace.model.record.Record;
import com.researchspace.service.EcatImageAnnotationManager;
import com.researchspace.service.FieldManager;
import com.researchspace.service.IMediaFactory;
import com.researchspace.service.IconImageManager;
import com.researchspace.service.ImageProcessor;
import com.researchspace.service.MediaManager;
import com.researchspace.service.RSChemElementManager;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Constraint;
import javax.validation.Payload;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/** This controller returns an image so that it may be displayed within an image tag or CSS */
@Controller
@RequestMapping({"/image", "/public/publicView/image"})
public class ImageController extends BaseController {

  private @Autowired EcatImageAnnotationManager ecatImageAnnotationManager;
  private @Autowired IconImageManager iconImageManager;
  private @Autowired MediaManager mediaManager;
  private @Autowired FieldManager fieldManager;
  private @Autowired ImageProcessor imgProcesser;
  private @Autowired IMediaFactory mediaFactory;

  private @Autowired RSChemElementManager rsChemElementManager;

  @Autowired
  @Qualifier("compositeDocumentConverter")
  private DocumentConversionService docConverter;

  static final byte MAX_THUMBNAIL_ATTEMPTS = 3;

  /**
   * Gets a Document thumbnail if it exists. Not authorised.
   *
   * <p>'unused' path variable should contain thumbnail id, which will allow for proper caching and
   * refreshing of a thumbnail if underlying document is changed at some point.
   *
   * @param ecatDocId document id
   * @return
   * @throws IOException
   */
  @GetMapping("/docThumbnail/{id}/{unused}")
  public ResponseEntity<byte[]> getDocThumbnail(
      @PathVariable("id") Long ecatDocId,
      @RequestParam(value = "revision", required = false) Long revisionId)
      throws IOException {

    User subject = userManager.getAuthenticatedUserInSession();
    EcatDocumentFile ecatDocumentFile =
        (EcatDocumentFile)
            baseRecordManager.retrieveMediaFile(
                subject,
                ecatDocId,
                revisionId,
                null,
                new EcatDocumentThumbnailInitializationPolicy(
                    new LinkedFieldsToMediaRecordInitPolicy()));

    Supplier<byte[]> byteSupplier = null;
    // return if exists
    if (ecatDocumentFile.getDocThumbnailFP() != null) {
      Optional<FileInputStream> fisOpt = fileStore.retrieve(ecatDocumentFile.getDocThumbnailFP());
      if (fisOpt.isPresent()) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(4096)) {
          IOUtils.copy(fisOpt.get(), bos);
          byteSupplier = bos::toByteArray;
        }
      }
    }
    // fallback in case migration failed, can delete once rspac-2186 complete
    else if (ecatDocumentFile.getThumbNail() != null) {
      log.info("No document thumbnail FP for {}, looking in DB", ecatDocId);
      ImageBlob blob = ecatDocumentFile.getThumbNail();
      byteSupplier = blob::getData;
      // create thumbnail and save it
    }
    // there's no stored thumbnail, create new one in filestore
    if (byteSupplier == null) {
      log.info("No document thumbnail for {}, creating new one", ecatDocId);
      // doc can't generate a thumbnail, don't try forever
      byte numAttempts = ecatDocumentFile.getNumThumbnailConversionAttemptsMade();
      if (numAttempts < MAX_THUMBNAIL_ATTEMPTS
          && docConverter.supportsConversion(ecatDocumentFile, "png")) {
        File secureTmpDir = IoUtils.createOrGetSecureTempDirectory().toFile();
        File tempFile = File.createTempFile("input", ".png", secureTmpDir);
        ConversionResult result = docConverter.convert(ecatDocumentFile, "png", tempFile);
        ecatDocumentFile.setNumThumbnailConversionAttemptsMade(++numAttempts);
        if (revisionId == null && result.isSuccessful()) {
          FileProperty fp =
              fileStore.createAndSaveFileProperty(
                  InternalFileStore.DOC_THUMBNAIL_CATEGORY,
                  subject,
                  result.getConverted().getName(),
                  new FileInputStream(result.getConverted()));
          ecatDocumentFile.setDocThumbnailFP(fp);
          byte[] data = FileUtils.readFileToByteArray(result.getConverted());
          byteSupplier = () -> data;
        }
        recordManager.save(ecatDocumentFile, subject);
      } else if (numAttempts >= MAX_THUMBNAIL_ATTEMPTS) {
        log.info(
            "Attempting thumbnail generation for doc {} has failed multiple times, not retrying",
            ecatDocId);
      }
    }

    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.IMAGE_PNG);
    // we couldn't make a thumbnail, use default icon
    if (byteSupplier == null) {
      byte[] bytes = mediaFactory.getFileSuffixIcon(ecatDocumentFile.getFileName());
      byteSupplier = () -> bytes;
    }
    setCacheTimeInBrowser(ResponseUtil.YEAR, null, headers);
    return new ResponseEntity<>(byteSupplier.get(), headers, HttpStatus.OK);
  }

  /**
   * This will retrieve an annotated image ( if it is annotated ) or the plain image if it is not
   * annotated - the URLs are the same.
   *
   * <p>If the revision ID is set, it'll retrieve either the image annotation at that revision, or
   * the image.
   *
   * @param idComposed parentId-imageId
   * @param unused - is just a timestamp to refresh the picture after adding annotations
   * @param revision - an optional revision number
   * @return The image bytes
   * @throws IOException
   */
  @GetMapping("/getImage/{id}/{unused}")
  public ResponseEntity<byte[]> getImage(
      @PathVariable("id") String idComposed,
      @PathVariable("unused") String unused,
      @RequestParam(value = "revision", required = false) Integer revision,
      @RequestParam(value = "fullImage", required = false, defaultValue = "false")
          Boolean fullImage)
      throws IOException {

    User user = userManager.getAuthenticatedUserInSession();
    validateComposedId(idComposed);
    long parentId = Long.parseLong(idComposed.split("-")[0]);
    long imageId = Long.parseLong(idComposed.split("-")[1]);

    EcatImageAnnotation ecatImageAnnotation =
        ecatImageAnnotationManager.getByParentIdAndImageId(parentId, imageId, user);

    if (ecatImageAnnotation == null) {
      return returnImageBytes(imageId, fullImage, false, user);
    }
    return returnImageAnnotationBytes(ecatImageAnnotation, revision, fullImage, user);
  }

  /**
   * This will retrieve bytes of annotation image. If the revision ID is set, it'll retrieve either
   * the image annotation at that revision, or the original image.
   *
   * @param id annotation id
   * @param unused - is just a timestamp to refresh the picture after adding annotations
   * @param revision - an optional revision number
   * @return The image annotation bytes
   * @throws IOException
   * @throws ObjectRetrievalFailureException if there is no annotation with given id
   */
  @GetMapping("/getAnnotation/{id}/{unused}")
  public ResponseEntity<byte[]> getAnnotation(
      @PathVariable("id") Long id,
      @PathVariable("unused") String unused,
      @RequestParam(value = "revision", required = false) Integer revision,
      @RequestParam(value = "fullImage", required = false, defaultValue = "false")
          Boolean fullImage,
      HttpServletResponse response)
      throws IOException {

    User user = userManager.getAuthenticatedUserInSession();
    EcatImageAnnotation ecatImageAnnotation = ecatImageAnnotationManager.get(id, user);
    new ResponseUtil().setCacheTimeInBrowser(3600 * 24 * 7, new Date(), response);
    return returnImageAnnotationBytes(ecatImageAnnotation, revision, fullImage, user);
  }

  private ResponseEntity<byte[]> returnImageAnnotationBytes(
      EcatImageAnnotation ecatImageAnnotation, Integer revision, Boolean fullImage, User user)
      throws IOException {
    // we check current for permissions since  envers does not load up enough object graph to check
    // permissions
    // see rspac-939
    EcatImageAnnotation currentForPermissions = ecatImageAnnotation;
    if (revision != null) {
      // this might be null if an image is now annotated in the current
      // version, but was
      // not annotated at the time of the specified revision.
      AuditedEntity<EcatImageAnnotation> audited =
          auditManager.getLinkableElementForRevision(
              EcatImageAnnotation.class, ecatImageAnnotation.getId(), revision);
      if (audited != null) {
        // we get the audited annotation if there was one
        ecatImageAnnotation = audited.getEntity();
        currentForPermissions = ecatImageAnnotationManager.get(ecatImageAnnotation.getId(), user);
      } else {
        // else we just return the image ( currently cannot be altered
        // so is the same in
        // all versions, so we may as well return the current one.
        return returnImageBytes(ecatImageAnnotation.getImageId(), fullImage, false, user);
      }
    }

    // so now, we are returning an image annotation data
    BaseRecord container = currentForPermissions.getRecord();
    assertAuthorisation(user, container, PermissionType.READ);
    return returnBytesAsResponseEntity(ecatImageAnnotation.getData());
  }

  private void validateComposedId(String idComposed) {
    if (StringUtils.isEmpty(idComposed)) {
      throw new IllegalArgumentException(" no id to retrieve");
    }
    if (!idComposed.matches("\\d+\\-\\d+")) {
      throw new IllegalArgumentException(
          "Incorrect id format - should be '\\d+\\-\\d+' but was '" + idComposed + "'");
    }
  }

  private ResponseEntity<byte[]> returnBytesAsResponseEntity(byte[] data) {
    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.IMAGE_PNG);
    return new ResponseEntity<>(data, headers, HttpStatus.OK);
  }

  private ResponseEntity<byte[]> returnImageBytes(
      long id, boolean returnFullImage, boolean convertTiff, User subject) throws IOException {
    EcatImage ecatImage = recordManager.getEcatImage(id, true);
    assertAuthorisation(subject, ecatImage, PermissionType.READ);

    String imgExtension = ecatImage.getExtension();
    byte[] data = null;
    final HttpHeaders headers = new HttpHeaders();
    if (ImageUtils.isTiff(imgExtension) && convertTiff && returnFullImage) {
      Optional<byte[]> tiffFileBytes = getTiffFileBytes(ecatImage);
      if (tiffFileBytes.isPresent()) {
        data = tiffFileBytes.get();
        headers.setContentType(MediaType.IMAGE_PNG);
      }
    } else {
      try (InputStream is = getInputStreamForImageBytes(ecatImage, returnFullImage)) {
        if (is != null) {
          log.info("Loading picture {}", id);
          data = IOUtils.toByteArray(is);
        }
      }
    }

    if (data == null) {
      log.error("Could not retrieve image bytes for image {}", ecatImage.getId());
      return createEmptyByte500Response(headers);
    }

    if (imgExtension.equals("jpeg") || imgExtension.equals("jpg")) {
      headers.setContentType(MediaType.IMAGE_JPEG);
    } else if (imgExtension.equals("gif")) {
      headers.setContentType(MediaType.IMAGE_GIF);
    } else if (imgExtension.equals("png")) {
      headers.setContentType(MediaType.IMAGE_PNG);
    }
    return new ResponseEntity<>(data, headers, HttpStatus.CREATED);
  }

  private Optional<byte[]> getTiffFileBytes(EcatImage ecatImage) throws IOException {
    Optional<byte[]> resultOpt = Optional.empty();
    File tiffFile = fileStore.findFile(ecatImage.getFileProperty());
    Optional<BufferedImage> buffImageOpt = ImageUtils.getBufferedImageFromTiffFile(tiffFile);
    if (buffImageOpt.isPresent()) {
      try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
        ImageIO.write(buffImageOpt.get(), "png", baos);
        baos.flush();
        return Optional.of(baos.toByteArray());
      }
    }
    return resultOpt;
  }

  private InputStream getInputStreamForImageBytes(EcatImage ecatImage, boolean returnFullImage) {
    InputStream is = null;
    if (returnFullImage) {
      is = fileStore.retrieve(ecatImage.getFileProperty()).orElse(null);
    } else {
      // get working image if possible, else thumbnail.
      if (ecatImage.getWorkingImageFP() != null) {
        is = fileStore.retrieve(ecatImage.getWorkingImageFP()).orElse(null);
      }
      if (is == null && ecatImage.getWorkingImage() != null) {
        is = new ByteArrayInputStream(ecatImage.getWorkingImage().getData());
      }
      if (is == null && ecatImage.getThumbnailImageFP() != null) {
        is = fileStore.retrieve(ecatImage.getThumbnailImageFP()).orElse(null);
      }
      if (is == null && ecatImage.getImageThumbnailed() != null) {
        is = new ByteArrayInputStream(ecatImage.getImageThumbnailed().getData());
      }
    }
    return is;
  }

  /**
   * @param idComposed parentId-imageId
   * @param unused - is just a timestamp, it is trick to refresh the picture on Firefox after adding
   *     annotations
   * @return
   * @throws IOException
   */
  @GetMapping("/getImageToAnnotate/{id}/{unused}")
  public ResponseEntity<byte[]> getImageToAnnotate(
      @PathVariable("id") String idComposed, @PathVariable("unused") String unused)
      throws IOException {

    validateComposedId(idComposed);
    long id = Long.parseLong(idComposed.split("-")[1]);

    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.IMAGE_PNG);

    EcatImage ecatImage = recordManager.getEcatImage(id, true);
    User subject = userManager.getAuthenticatedUserInSession();
    assertAuthorisation(subject, ecatImage, PermissionType.READ);

    try (InputStream is = getInputStream(ecatImage)) {
      if (is == null) {
        log.error("Could not retrieve image bytes for image {}", ecatImage.getId());
        return createEmptyByte500Response(headers);
      }
      byte[] data = IOUtils.toByteArray(is);
      return new ResponseEntity<>(data, headers, HttpStatus.CREATED);
    }
  }

  private ResponseEntity<byte[]> createEmptyByte500Response(final HttpHeaders headers) {
    return new ResponseEntity<>(new byte[0], headers, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private InputStream getInputStream(EcatImage ecatImage) {
    InputStream is = null;
    if (ecatImage.getWorkingImageFP() != null) {
      is = fileStore.retrieve(ecatImage.getWorkingImageFP()).orElse(null);
    } else if (ecatImage.getWorkingImage() != null) {
      is = new ByteArrayInputStream(ecatImage.getWorkingImage().getData());
    } else {
      is = fileStore.retrieve(ecatImage.getFileProperty()).orElse(null);
    }
    return is;
  }

  /**
   * @param sketchId
   * @param unused - is just a timestamp, it is trick to refresh the picture on Firefox after adding
   *     annotations
   * @param revision
   * @return
   * @throws IOException
   */
  @GetMapping("/getImageSketch/{id}/{unused}")
  public ResponseEntity<byte[]> getImageSketch(
      @PathVariable("id") Long sketchId,
      @PathVariable("unused") String unused,
      @RequestParam(value = "revision", required = false) Integer revision) {
    User u = userManager.getAuthenticatedUserInSession();
    return returnSketchImage(sketchId, revision, u);
  }

  /**
   * @param sketchId
   * @param revision
   * @return
   * @throws IOException
   */
  @GetMapping("/getImageSketch/{id}")
  public ResponseEntity<byte[]> getImageSketch(
      @PathVariable("id") Long sketchId,
      @RequestParam(value = "revision", required = false) Integer revision) {
    User u = userManager.getAuthenticatedUserInSession();
    return returnSketchImage(sketchId, revision, u);
  }

  private ResponseEntity<byte[]> returnSketchImage(Long sketchId, Integer revision, User user) {
    EcatImageAnnotation ecatImageAnnotation = null;
    EcatImageAnnotation ecatImageCurrent = null;
    if (revision == null) {
      ecatImageAnnotation = ecatImageAnnotationManager.get(sketchId, null);
      ecatImageCurrent = ecatImageAnnotation;
    } else {
      ecatImageAnnotation =
          auditManager
              .getLinkableElementForRevision(EcatImageAnnotation.class, sketchId, revision)
              .getEntity();
      // failover for case where there is no audit table pre 0.8
      if (ecatImageAnnotation == null) {
        ecatImageAnnotation = ecatImageAnnotationManager.get(sketchId, null);
        ecatImageCurrent = ecatImageAnnotation;
      } else {
        ecatImageCurrent = ecatImageAnnotationManager.get(sketchId, null);
      }
    }

    assertAuthorisation(user, ecatImageCurrent.getRecord(), PermissionType.READ);
    return returnBytesAsResponseEntity(ecatImageAnnotation.getData());
  }

  /**
   * Loads ecatImage annotation json string when loading sketcher
   *
   * @param parentId
   * @param imageId
   * @return {@link AjaxReturnObject} of type String with annotation data
   */
  @GetMapping("ajax/loadImageAnnotations")
  @ResponseBody
  public AjaxReturnObject<String> loadImageAnnotations(
      @RequestParam("parentId") long parentId,
      @RequestParam("imageId") long imageId,
      @RequestParam(value = "revision", required = false) Integer revision) {

    User subject = userManager.getAuthenticatedUserInSession();
    EcatImageAnnotation annotation =
        ecatImageAnnotationManager.getByParentIdAndImageId(parentId, imageId, subject);
    if (revision != null && annotation != null) {
      annotation =
          auditManager
              .getObjectForRevision(EcatImageAnnotation.class, annotation.getId(), revision)
              .getEntity();
    }
    return returnAnnotationData(annotation, subject);
  }

  /**
   * Loads sketch json string when loading sketcher
   *
   * @param sketchId the id of the sketch.
   * @param revision an optional revision number
   * @return {@link AjaxReturnObject} of type String with annotation data
   */
  @GetMapping("ajax/loadSketchImageAnnotations")
  @ResponseBody
  public AjaxReturnObject<String> loadSketchAnnotations(
      @RequestParam("sketchId") long sketchId,
      @RequestParam(value = "revision", required = false) Integer revision) {

    User u = userManager.getAuthenticatedUserInSession();
    EcatImageAnnotation ecatImageAnnotation = null;
    if (revision == null) {
      ecatImageAnnotation = ecatImageAnnotationManager.get(sketchId, null);
    } else {
      ecatImageAnnotation =
          auditManager
              .getObjectForRevision(EcatImageAnnotation.class, sketchId, revision)
              .getEntity();
    }
    return returnAnnotationData(ecatImageAnnotation, u);
  }

  private AjaxReturnObject<String> returnAnnotationData(
      EcatImageAnnotation ecatImageAnnotation, User u) {
    if (ecatImageAnnotation == null) {
      return new AjaxReturnObject<>("", null);
    }
    Record imageAnnotationRecord = recordManager.get(ecatImageAnnotation.getRecord().getId());
    assertAuthorisation(u, imageAnnotationRecord, PermissionType.READ);
    return new AjaxReturnObject<>(ecatImageAnnotation.getAnnotations(), null);
  }

  /**
   * Method to save an annotated image (EcatImageAnnotation).
   *
   * @param annotations
   * @param imageBase64
   * @param fieldId
   * @param imageId
   * @return EcatImageAnnotation
   * @throws IOException
   */
  @PostMapping("ajax/saveImageAnnotation")
  @IgnoreInLoggingInterceptor(ignoreRequestParams = {"image"})
  @ResponseBody
  public AjaxReturnObject<EcatImageAnnotation> saveImageAnnotation(
      @RequestParam("annotations") String annotations,
      @RequestParam("image") String imageBase64,
      @RequestParam("parentId") long fieldId,
      @RequestParam("imageId") long imageId,
      Principal principal)
      throws IOException {

    User subject = getUserByUsername(principal.getName());
    Field field = fieldManager.get(fieldId, subject).get();
    EcatImageAnnotation ecatImageAnnotation =
        mediaManager.saveImageAnnotation(
            annotations, imageBase64, fieldId, field.getStructuredDocument(), imageId, subject);
    ecatImageAnnotation.setData(null);
    return new AjaxReturnObject<>(ecatImageAnnotation, null);
  }

  @PostMapping("ajax/saveSketch")
  @IgnoreInLoggingInterceptor(ignoreRequestParams = {"image"})
  @ResponseBody
  public AjaxReturnObject<EcatImageAnnotation> saveSketch(
      @RequestParam("annotations") String annotations,
      @RequestParam("image") String imageBase64,
      @RequestParam("sketchId") String sketchId,
      @RequestParam("fieldId") long fieldId)
      throws Exception {

    User subject = userManager.getAuthenticatedUserInSession();
    Field field = fieldManager.get(fieldId, subject).get();
    EcatImageAnnotation ecatImageAnnotation =
        mediaManager.saveSketch(
            annotations,
            imageBase64,
            sketchId,
            field.getId(),
            field.getStructuredDocument(),
            subject);
    ecatImageAnnotation.setData(null);
    return new AjaxReturnObject<>(ecatImageAnnotation, null);
  }

  /**
   * Controller method to display icon image, in case of on icon will not affect main operation,
   * catch Exception internally
   *
   * @param id
   * @param response
   */
  @IgnoreInLoggingInterceptor(ignoreAll = true)
  @GetMapping("/getIconImage/{id}")
  public void getIconImage(@PathVariable("id") Long id, HttpServletResponse response) {
    try {
      byte[] data = null;
      if (id < 0) {
        InputStream in = getIconImageFromFolder("icons/text.png");
        data = IOUtils.toByteArray(in);
      } else {
        IconEntity iconEntity = iconImageManager.getIconEntity(id);
        if (iconEntity == null) {
          InputStream in = getIconImageFromFolder("icons/text.png");
          data = IOUtils.toByteArray(in);
        } else {
          data = iconEntity.getIconImage();
        }
      }
      // setup caching for icon image in browser.
      new ResponseUtil().setCacheTimeInBrowser(3600 * 24 * 65, new Date(), response);
      response.setContentType("image/jpeg");
      response.setContentLength(data.length);
      response.getOutputStream().write(data);
    } catch (IOException ex) {
      log.warn("Error getting icon image[" + id + "]" + ex.getMessage());
    }
  }

  private InputStream getIconImageFromFolder(String pathRelativeToImages) {
    return servletContext.getResourceAsStream("/images/" + pathRelativeToImages);
  }

  @GetMapping("/ajax/imageInfo")
  @ResponseBody
  public List<ImageInfo> getImageInfo(@RequestParam("ids[]") Long[] imageIds) {
    List<Long> images = List.of(imageIds);
    User subject = userManager.getAuthenticatedUserInSession();
    List<Record> authorisedRecords =
        recordManager.getAuthorisedRecordsById(images, subject, PermissionType.READ);
    List<ImageInfo> imageInfos = new ArrayList<>();
    for (Record authorisedRecord : authorisedRecords) {
      if (authorisedRecord instanceof EcatImage) {
        imageInfos.add(new ImageInfo((EcatImage) authorisedRecord));
        // Hack to get photoswipe working with chemistry files in the gallery
      } else if (authorisedRecord instanceof EcatChemistryFile) {
        imageInfos.add(getImageInfoFromChemistryFile((EcatChemistryFile) authorisedRecord));
      }
    }
    return imageInfos;
  }

  private ImageInfo getImageInfoFromChemistryFile(EcatChemistryFile r) {
    ImageInfo imageInfo = new ImageInfo();
    imageInfo.setHeight(1000);
    imageInfo.setWidth(1000);
    imageInfo.setName(r.getName());
    imageInfo.setId(r.getId());
    return imageInfo;
  }

  @Max(3)
  @Min(0)
  @NotNull
  @Target({ElementType.FIELD, ElementType.METHOD})
  @Retention(RetentionPolicy.RUNTIME)
  @Constraint(validatedBy = {})
  @Documented
  public @interface ValidRotation {

    String message() default "Image rotation must be an integer  0 <= i < 4";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
  }

  @Data
  public static class RotationConfig {
    static final int MAX_IDS_TO_PROCESS = 50;

    @Size(min = 1, max = MAX_IDS_TO_PROCESS, message = "{errors.collection.range} image ids")
    private List<Long> idsToRotate = new ArrayList<>();

    @ValidRotation Byte timesToRotate;
  }

  /**
   * Rotates one or more EcatImage from Gallery, all with the same parent.
   *
   * @param rotationConfig a Long [] of record ids of records to rotate and an Integer of
   *     90*timesToRotate
   * @return A {@link @ResponseBody}
   * @throws IOException
   */
  @PostMapping("/ajax/rotateImageGalleries")
  @ResponseBody
  public AjaxReturnObject<Boolean> rotateGalleries(
      @Valid @RequestBody RotationConfig rotationConfig, BindingResult errors, Principal principal)
      throws IOException {
    if (errors.hasErrors()) {
      ErrorList el = new ErrorList();
      inputValidator.populateErrorList(errors, el);
      return new AjaxReturnObject<>(null, el);
    }
    User subject = getUserByUsername(principal.getName());
    for (Long id : rotationConfig.getIdsToRotate()) {
      boolean isRecord = isRecord(id);
      if (isRecord) {
        doImageRotation(rotationConfig.getTimesToRotate(), subject, id);
      }
    }
    return new AjaxReturnObject<>(true, null);
  }

  private Optional<EcatImage> doImageRotation(byte timesToRotate, User subject, Long id)
      throws IOException {
    EcatImage ecatImage = recordManager.getEcatImage(id, true);
    assertAuthorisation(subject, ecatImage, PermissionType.READ);
    return imgProcesser.rotate(ecatImage, timesToRotate, subject);
  }

  /*
   * Front-end image editing methods
   */

  /**
   * Gets fullsize gallery image in a format that can be displayed in the browser.
   *
   * <p>For example: jpg will be streamed directly from filestore, but tiff will be converted to png
   * first.
   *
   * @param imageId
   */
  @GetMapping("/getImageForEdit/{id}/{unused}")
  public ResponseEntity<byte[]> getImageForEdit(@PathVariable("id") Long imageId)
      throws IOException {

    User user = userManager.getAuthenticatedUserInSession();
    return returnImageBytes(imageId, true, true, user);
  }

  /**
   * Saves image provided in PNG/base64 format as a new Gallery item. Also creates a link to the
   * original source image.
   *
   * @param data object with id of original image and new base64
   * @return id of a newly created image
   */
  @PostMapping("ajax/saveEditedImage")
  @IgnoreInLoggingInterceptor(ignoreRequestParams = {"imageBase64"})
  @ResponseBody
  public AjaxReturnObject<Long> saveEditedImage(@RequestBody EditedImageData data)
      throws IOException {

    User user = userManager.getAuthenticatedUserInSession();
    EcatImage editedSrcImage = recordManager.getEcatImage(data.getImageId(), false);
    assertAuthorisation(user, editedSrcImage, PermissionType.READ);

    EcatImage newImage = mediaManager.saveEditedImage(editedSrcImage, data.getImageBase64(), user);
    logToAuditTrail(user, editedSrcImage, newImage);
    return new AjaxReturnObject<>(newImage.getId(), null);
  }

  private void logToAuditTrail(User user, EcatImage editedSrcImage, EcatImage newImage) {
    String logMessageString =
        String.format(
            "Edited image GL%d created from source image GL%d",
            newImage.getId(), editedSrcImage.getId());
    auditService.notify(new GenericEvent(user, newImage, AuditAction.CREATE, logMessageString));
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  protected static class EditedImageData {
    private Long imageId;
    private String imageBase64;
  }
}
