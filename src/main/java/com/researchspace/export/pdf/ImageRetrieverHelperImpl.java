package com.researchspace.export.pdf;

import static com.researchspace.linkedelements.RichTextUpdater.parseImageIdFromSrcURLInTextField;

import com.researchspace.core.util.LimitedBytesFromURLRetriever;
import com.researchspace.files.service.FileStore;
import com.researchspace.linkedelements.RichTextUpdater;
import com.researchspace.linkedelements.RichTextUpdater.ImageURL;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatImageAnnotation;
import com.researchspace.model.FileProperty;
import com.researchspace.model.IFieldLinkableElement;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.RSMath;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.service.AuditManager;
import com.researchspace.service.EcatImageAnnotationManager;
import com.researchspace.service.MediaManager;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.RSMathManager;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

public class ImageRetrieverHelperImpl implements ImageRetrieverHelper {

  private static final Logger LOG = LoggerFactory.getLogger(ImageRetrieverHelperImpl.class);

  private @Autowired ResourceLoader resourceLocator;
  private @Autowired @Qualifier("compositeFileStore") FileStore fileStore;
  private @Autowired IPermissionUtils permissions;
  private @Autowired EcatImageAnnotationManager imgAnnotationManager;
  private @Autowired RSChemElementManager chemElementManager;
  private @Autowired MediaManager mediaManager;
  private @Autowired RSMathManager mathManager;
  private @Autowired AuditManager auditManager;

  private static final int EXTERNAL_IMAGE_TIMEOUT_MS = 2000;
  final int MAX_IMG_SIZE_BYTES = 10_000_000;

  public byte[] getImageBytesFromImgSrc(String imgSrcValue, ExportToFileConfig config)
      throws IOException {
    byte[] imageData = null;
    try {
      if (isStaticResourceIcon(imgSrcValue)) {
        imageData = getStaticData(imgSrcValue);
      } else if (isChemImage(imgSrcValue)) {
        imageData = fetchChemImage(imgSrcValue, config);
      } else if (imgSrcValue.contains("/svg/")) {
        imageData = fetchMathEquationImage(imgSrcValue, config);
      } else if (imgSrcValue.contains("getImageSketch")) {
        imageData = fetchSketchOrNewAnnotationImage(imgSrcValue, config);
      } else if (imgSrcValue.contains("getAnnotation")) {
        // depending on config we either get original image or the new annotation
        imageData = fetchSketchOrNewAnnotationImage(imgSrcValue, config);
        // if it's null then we need to get the old annotated image.
      } else {
        imageData = fetchImageOrOldAnnotatedImage(imgSrcValue, config);
      }
    } catch (Exception e) {
      // this prevents export blowing up if 1 item couldn't be found
      LOG.warn("Couldn't retrieve image for src [{}] : {}", imgSrcValue, e.getMessage());
    }
    // fallback so as never to return null image.
    if (imageData == null) {
      if (imgSrcValue.startsWith("http")) {
        imageData = retrieveUrlImage(imgSrcValue);
      } else {
        imageData = getUnknown();
      }
    }
    return imageData;
  }

  private byte[] getStaticData(String imgSrcValue) throws IOException {
    byte[] imageData = null;
    Resource icon = resourceLocator.getResource(imgSrcValue);
    if (!icon.exists() || !icon.isReadable()) {
      LOG.warn("icon with path {} cannot be loaded", imgSrcValue);
    } else {
      imageData = new byte[(int) icon.contentLength()];
      IOUtils.read(icon.getInputStream(), imageData);
    }
    return imageData;
  }

  byte[] getUnknown() {
    String fallBackImage = "/images/icons/unknownDocument.png";
    try {
      return getStaticData(fallBackImage);
    } catch (IOException e) {
      // should never happen,,
      LOG.error("The default fallback image '{}' couldn't be loaded", fallBackImage, e);
      return null;
    }
  }

  // as we are getting from a URL, we have no idea how big is the image so there has to be some
  // limit
  // to avoid malicious or accidental OOM for large images
  private byte[] retrieveUrlImage(String imgSrcValue) throws IOException {
    LimitedBytesFromURLRetriever retriever =
        new LimitedBytesFromURLRetriever(EXTERNAL_IMAGE_TIMEOUT_MS, MAX_IMG_SIZE_BYTES);
    return retriever.retrieveUrlBytesQuietly(imgSrcValue, () -> getUnknown());
  }

  private byte[] fetchChemImage(String src, ExportToFileConfig exportConfig) {
    ImageURL srcUrl = RichTextUpdater.parseImageIdFromSrcURLInTextField(src, "CHEM");
    RSChemElement chemElement =
        chemElementManager.get(srcUrl.getImageId(), exportConfig.getExporter());
    if (chemElement != null) {
      return chemElement.getDataImage();
    }
    return null;
  }

  private byte[] fetchMathEquationImage(String src, ExportToFileConfig exportConfig) {
    ImageURL img = RichTextUpdater.parseImageIdFromSrcURLInTextField(src, "");
    RSMath mathElement = mathManager.get(img.getImageId(), null, exportConfig.getExporter(), true);
    if (mathElement != null) {
      byte[] data = mathElement.getMathSvg().getData();

      try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
        new SvgToPngConverter().convert(new String(data, StandardCharsets.UTF_8), baos);
        return baos.toByteArray();
      } catch (IOException | TranscoderException e) {
        LOG.warn("Couldn't convert math equation to png src [{}] : {}", src, e.getMessage());
      }
    }
    return null;
  }

  private byte[] fetchSketchOrNewAnnotationImage(String imgURL, ExportToFileConfig exportConfig) {
    ImageURL img = RichTextUpdater.parseImageIdFromSrcURLInTextField(imgURL, "");
    EcatImageAnnotation annotation =
        imgAnnotationManager.get(img.getImageId(), exportConfig.getExporter());
    if (!checkPerms(annotation, exportConfig)) {
      return null;
    }
    if (annotation != null) {
      if (exportConfig.isAnnotations() || img.isSketchURL()) {
        return annotation.getData();
      } else if (!img.isSketchURL()) {
        return fetchEcatImage(annotation.getImageId(), null, exportConfig);
      }
    }
    return null;
  }

  boolean checkPerms(IFieldLinkableElement toCheck, ExportToFileConfig exportConfig) {
    return permissions.isPermittedViaMediaLinksToRecords(
        toCheck, PermissionType.READ, exportConfig.getExporter());
  }

  public void setImgAnnotationManager(EcatImageAnnotationManager imgAnnotationManager) {
    this.imgAnnotationManager = imgAnnotationManager;
  }

  private byte[] fetchImageOrOldAnnotatedImage(
      String imgSrcValue, ExportToFileConfig exportConfig) {

    ImageURL imgURL = parseImageIdFromSrcURLInTextField(imgSrcValue, "IMAGE");
    if (imgURL == null) {
      return null;
    }

    byte[] result = null;

    EcatImageAnnotation annotation =
        imgAnnotationManager.getByParentIdAndImageId(
            imgURL.getParentId(), imgURL.getImageId(), exportConfig.getExporter());
    if (annotation != null && exportConfig.isAnnotations()) {
      result = annotation.getData();
    }
    // if no annotation, we try to get the image.
    if (result == null) {
      result = fetchEcatImage(imgURL.getImageId(), imgURL.getRevision(), exportConfig);
    }
    return result;
  }

  private byte[] fetchEcatImage(Long imageId, Long revision, ExportToFileConfig exportConfig) {

    EcatImage ecatImage;
    if (revision == null) {
      ecatImage = mediaManager.getImage(imageId, exportConfig.getExporter(), true);
    } else {
      AuditedEntity<EcatImage> auditedImage =
          auditManager.getObjectForRevision(EcatImage.class, imageId, revision);
      if (auditedImage == null) {
        return null;
      }
      ecatImage = auditedImage.getEntity();
    }
    // actual file could be huge, so we use the resized one if possible
    byte[] workingImage = getWorkingImage(ecatImage);
    if (workingImage.length > 0) {
      return workingImage;
    } else {
      FileProperty from = ecatImage.getFileProperty();
      return bytesFromFileProperty(from);
    }
  }

  private byte[] bytesFromFileProperty(FileProperty from) {
    return fileStore
        .retrieve(from)
        .map(
            is -> {
              try {
                return IOUtils.toByteArray(is);
              } catch (IOException e) {
                return emptyByteArray(from);
              }
            })
        .orElse(emptyByteArray(from));
  }

  private byte[] emptyByteArray(FileProperty from) {
    LOG.error("Could not open file stream on FileProperty {} ", from.getId());
    return new byte[0];
  }

  private byte[] getWorkingImage(EcatImage ecatImage) {
    if (ecatImage.getWorkingImageFP() != null) {
      byte[] fpBytes = bytesFromFileProperty(ecatImage.getWorkingImageFP());
      return fpBytes;
    } else if (ecatImage.getWorkingImage() != null) {
      return ecatImage.getWorkingImage().getData();
    } else {
      return new byte[0];
    }
  }

  boolean isChemImage(String imgSrcValue) {
    return imgSrcValue.contains("getImageChem") || imgSrcValue.contains("sourceType=CHEM");
  }

  boolean isStaticResourceIcon(String imgSrcValue) {
    return imgSrcValue.startsWith("/images/icons")
        || imgSrcValue.startsWith("../resources/")
        || imgSrcValue.startsWith("/images");
  }
}
