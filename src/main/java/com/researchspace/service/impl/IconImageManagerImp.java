package com.researchspace.service.impl;

import com.researchspace.dao.IconImgDao;
import com.researchspace.model.ImageBlob;
import com.researchspace.model.record.IconEntity;
import com.researchspace.service.IconImageManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("iconImageManager")
public class IconImageManagerImp extends GenericManagerImpl<ImageBlob, Long>
    implements IconImageManager {
  private IconImgDao iconImgDao;

  @Autowired
  public void setIconImgDao(IconImgDao iconImgDao) {
    super.setDao(iconImgDao);
    this.iconImgDao = iconImgDao;
  }

  @Override
  public IconEntity getIconEntity(Long id) {
    return iconImgDao.getIconEntity(id);
  }

  @Override
  public Optional<IconEntity> getIconEntity(
      Long id, OutputStream outstream, Supplier<byte[]> defaultIconSupplier) throws IOException {
    IconEntity iconEntity = iconImgDao.getIconEntity(id);
    byte[] data = null;

    if (iconEntity != null) {
      data = iconEntity.getIconImage();
    } else {
      data = defaultIconSupplier.get();
    }
    try (ByteArrayInputStream is = new ByteArrayInputStream(data);
        OutputStream out2 = outstream) {
      IOUtils.copy(is, out2);
    }
    return Optional.ofNullable(iconEntity);
  }

  @Override
  public IconEntity saveIconEntity(IconEntity iconEntity, boolean updateRSFormTable) {
    return iconImgDao.saveIconEntity(iconEntity, updateRSFormTable);
  }

  public List<Long> getAllIconIds() {
    return iconImgDao.getAllIconIds();
  }
}
