package com.researchspace.service.impl;

import static com.researchspace.files.service.InternalFileStore.THUMBNAIL_THUMBNAIL_CATEGORY;

import com.researchspace.core.util.imageutils.ImageUtils;
import com.researchspace.dao.EcatImageDao;
import com.researchspace.dao.ThumbnailDao;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.EcatImage;
import com.researchspace.model.FileProperty;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.Thumbnail;
import com.researchspace.model.Thumbnail.SourceType;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.service.AuditManager;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.ThumbnailManager;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.apache.commons.io.IOUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service("thumbnailManager")
public class ThumbnailManagerImpl extends GenericManagerImpl<Thumbnail, Long>
    implements ThumbnailManager, ApplicationListener<ApplicationEvent> {

  private @Autowired EcatImageDao ecatImageDao;
  private @Autowired RSChemElementManager chemElementManager;
  private @Autowired @Qualifier("compositeFileStore") FileStore fileStore;
  private @Autowired ThumbnailDao thumbnailDao;
  private @Autowired IPermissionUtils permUtils;
  private @Autowired AuditManager auditManager;

  @Autowired
  public void setThumbnailDao(ThumbnailDao thumbnailDao) {
    this.thumbnailDao = thumbnailDao;
    super.setDao(thumbnailDao);
  }

  @Override
  @CachePut(
      value = "com.researchspace.model.Thumbnail",
      key = "#thumbnail",
      condition = "#thumbnail.id ne null")
  public Thumbnail save(Thumbnail thumbnail, User subject) {
    try {
      Thumbnail savedThumbnail = thumbnailDao.save(thumbnail);
      return savedThumbnail;
    } catch (IllegalArgumentException exception) {
      log.error("Failed to save thumbnail", exception);
      return null;
    }
  }

  @Override
  public byte[] getThumbnailData(Long thumbnailId) {
    Thumbnail thumbnail = thumbnailDao.get(thumbnailId);

    if (thumbnail.getThumbnailFP() != null) {
      return fileStore
          .retrieve(thumbnail.getThumbnailFP())
          .map(
              is -> {
                try {
                  return IOUtils.toByteArray(is);
                } catch (IOException e) {
                  return new byte[0];
                }
              })
          .orElse(new byte[0]);
    } else {
      return thumbnail.getImageBlob().getData();
    }
  }

  // caching seems to have no effect as thumbnailID is always null.
  @Override
  @Cacheable(
      value = "com.researchspace.model.Thumbnail",
      key = "#thumbnail",
      condition = "#thumbnail.id ne null")
  public Thumbnail getThumbnail(Thumbnail thumbnail, User subject)
      throws IllegalArgumentException, IOException {
    Thumbnail existingThumbnail = thumbnailDao.getThumbnail(thumbnail);
    // If we have a matching thumbnail, return it
    if (existingThumbnail != null) {
      checkPerms(subject, existingThumbnail);
      return existingThumbnail;
    }

    // Otherwise, generate and return a new thumbnail
    Thumbnail newThumbnail = generateThumbnail(thumbnail, subject);
    save(newThumbnail, subject);
    checkPerms(subject, newThumbnail);
    return newThumbnail;
  }

  private void checkPerms(User subject, Thumbnail newThumbnail) {
    BaseRecord el = getEntityForSourceType(subject, newThumbnail);
    if (!(permUtils.isPermitted(el, PermissionType.READ, subject)
        || permUtils.isPermittedViaMediaLinksToRecords(el, PermissionType.READ, subject))) {
      throw new AuthorizationException();
    }
  }

  private BaseRecord getEntityForSourceType(User subject, Thumbnail newThumbnail) {
    if (newThumbnail.isImageThumbnail()) {
      return ecatImageDao.get(newThumbnail.getSourceId());
    } else if (newThumbnail.isChemThumbnail()) {
      return chemElementManager.get(newThumbnail.getSourceId(), subject).getRecord();
    } else {
      throw new IllegalStateException("Unknown image source type");
    }
  }

  private Thumbnail generateThumbnail(Thumbnail example, User subject)
      throws IllegalArgumentException, IOException {
    if (example.isImageThumbnail()) {
      return createThumbnailForImage(example, subject);
    }
    if (example.isChemThumbnail()) {
      return generateChemThumbnail(example, subject);
    }

    throw new IllegalArgumentException(
        "Thumbnail SourceType not recognised: " + example.getSourceType());
  }

  private Thumbnail generateChemThumbnail(Thumbnail example, User subject) throws IOException {

    RSChemElement rsChemElement = chemElementManager.get(example.getSourceId(), subject);
    String fileName = rsChemElement.getOid().toString() + ".png";

    try (InputStream is = new ByteArrayInputStream(rsChemElement.getDataImage())) {
      return createThumbnailFromInputStream(is, example, subject, fileName);
    }
  }

  private Thumbnail createThumbnailForImage(Thumbnail example, User subject)
      throws IllegalArgumentException, IOException {

    EcatImage ecatImage;
    if (example.getRevision() == null) {
      ecatImage = ecatImageDao.get(example.getSourceId());
    } else {
      AuditedEntity<EcatImage> audited =
          auditManager.getObjectForRevision(
              EcatImage.class, example.getSourceId(), example.getRevision());
      if (audited != null) {
        ecatImage = audited.getEntity();
      } else {
        throw new IllegalStateException("asked for image revision but retrieved null");
      }
    }
    InputStream is = getInputStreamForImage(ecatImage);
    return createThumbnailFromInputStream(is, example, subject, ecatImage.getFileName());
  }

  // get WI file property, WI image blob, or
  private InputStream getInputStreamForImage(EcatImage ecatImage) {
    InputStream is;
    if (ecatImage.getWorkingImageFP() != null) {
      is = streamFileProperty(ecatImage, ecatImage.getWorkingImageFP());
    } else if (ecatImage.getWorkingImage() != null) {
      is = new ByteArrayInputStream(ecatImage.getWorkingImage().getData());
    } else {
      is = streamFileProperty(ecatImage, ecatImage.getFileProperty());
    }
    return is;
  }

  private InputStream streamFileProperty(EcatImage ecatImage, FileProperty fp) {
    InputStream is = null;
    Optional<FileInputStream> fisOpt = fileStore.retrieve(fp);
    if (fisOpt.isPresent()) {
      is = fisOpt.get();
    } else {
      log.error("Could not retrieve image {} -  {}", ecatImage.getName(), ecatImage.getId());
      throw new IllegalStateException("Could not retrieve image file " + ecatImage.getId());
    }
    return is;
  }

  private Thumbnail createThumbnailFromInputStream(
      InputStream is, Thumbnail exampleThumbnail, User subject, String originalFileName)
      throws IOException {

    Optional<BufferedImage> image = ImageUtils.getBufferedImageFromUploadedFile("png", is);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    //noinspection OptionalGetWithoutIsPresent
    ImageUtils.createThumbnail(
        image.get(), exampleThumbnail.getWidth(), exampleThumbnail.getHeight(), baos, "png");

    try (ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray())) {
      FileProperty thumbnailFP =
          fileStore.createAndSaveFileProperty(
              THUMBNAIL_THUMBNAIL_CATEGORY, subject, originalFileName, bais);
      exampleThumbnail.setThumbnailFP(thumbnailFP);
    }
    return exampleThumbnail;
  }

  // just clear the whole cache as it's simpler than iterating over entries to delete,
  // and shouldn't be a big problem either
  @CacheEvict(value = "com.researchspace.model.Thumbnail", allEntries = true)
  @Override
  public void deleteImageThumbnails(EcatImage img, User user) {
    thumbnailDao.deleteAllThumbnails(SourceType.IMAGE, img.getId());
  }

  @Override
  public void onApplicationEvent(ApplicationEvent event) {
    if (event instanceof ThumbnailSourceUpdateEvent) {
      ThumbnailSourceUpdateEvent thumbnailSourceUpdateEvent = (ThumbnailSourceUpdateEvent) event;
      log.debug(
          "Received thumbnail source update event for type {}, id {}, parentId {}",
          thumbnailSourceUpdateEvent.getSourceType(),
          thumbnailSourceUpdateEvent.getSourceId(),
          thumbnailSourceUpdateEvent.getSourceParentId());
      deleteAllThumbnails(
          thumbnailSourceUpdateEvent.getSourceType(),
          thumbnailSourceUpdateEvent.getSourceId(),
          thumbnailSourceUpdateEvent.getSourceParentId());
    }
  }

  private void deleteAllThumbnails(SourceType sourceType, Long sourceId, Long sourceParentId) {
    log.debug(
        "Deleting thumbnails for type {}, id {}, parentId {} ",
        sourceType,
        sourceId,
        sourceParentId);
    int deletedCount = thumbnailDao.deleteAllThumbnails(sourceType, sourceId, sourceParentId);
    log.debug(
        "Deleted {}  thumbnails for type {}, id {}, parent id {}",
        deletedCount,
        sourceType,
        sourceId,
        sourceParentId);
  }
}
