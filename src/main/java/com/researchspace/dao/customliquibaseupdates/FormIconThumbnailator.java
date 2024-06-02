package com.researchspace.dao.customliquibaseupdates;

import static com.researchspace.core.util.imageutils.ImageUtils.scaleImageToWidthWithAspectRatio;
import static com.researchspace.core.util.imageutils.ImageUtils.toBytes;
import static org.apache.commons.lang.StringUtils.isEmpty;

import com.researchspace.core.util.imageutils.ImageUtils;
import com.researchspace.model.record.IconEntity;
import com.researchspace.service.IconImageManager;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import liquibase.database.Database;
import org.apache.commons.lang.ArrayUtils;

/** For RSPAC-827 */
public class FormIconThumbnailator extends AbstractCustomLiquibaseUpdater {
  private static final int THUMBAIL_DIMENSION = 64;
  private IconImageManager iconMgr;

  @Override
  public String getConfirmationMessage() {
    return "Converted all form icons > 64x64 to thumbnails";
  }

  @Override
  protected void doExecute(Database database) {
    List<Long> ids = iconMgr.getAllIconIds();
    for (Long id : ids) {
      IconEntity entity = iconMgr.getIconEntity(id);
      if (!isEmpty(entity.getImgName()) && entity.getImgName().endsWith("_icon")) {
        BufferedImage img = null;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
          if (ArrayUtils.isEmpty(entity.getIconImage())) {
            logger.info("Skipping icon entity {},  there are no image bytes!", entity.getId());
            continue;
          }

          Optional<BufferedImage> optionalImage =
              ImageUtils.getBufferedImageFromInputImageStream(
                  new ByteArrayInputStream(entity.getIconImage()));

          if (!optionalImage.isPresent()) {
            logger.info(
                "Skipping icon entity {},  image could not be created from bytes", entity.getId());
            continue;
          }
          img = optionalImage.get();
          if (img.getWidth() <= THUMBAIL_DIMENSION) {
            logger.info(
                "Skipping icon entity {},  is already thumnail size - {}x{}px",
                entity.getId(),
                entity.getWidth(),
                entity.getHeight());
            continue;
          }
          logger.info(
              "scaling icon entity {} which is  - {}x{}px",
              entity.getId(),
              entity.getWidth(),
              entity.getHeight());
          BufferedImage scaled = scaleImageToWidthWithAspectRatio(img, THUMBAIL_DIMENSION);
          entity.setHeight(scaled.getHeight());
          entity.setWidth(scaled.getWidth());
          entity.setIconImage(toBytes(scaled, "png"));
          iconMgr.saveIconEntity(entity, false);
        } catch (IOException e) {
          logger.warn("Could not convert iconentity {} to thumnail", entity.getId());
        }
      }
    }
  }

  @Override
  protected void addBeans() {
    this.iconMgr = context.getBean(IconImageManager.class);
  }
}
