package com.researchspace.dao;

import com.researchspace.model.ImageBlob;
import com.researchspace.model.record.IconEntity;
import java.util.List;

/** Handles saving/loading icons /image blobs */
public interface IconImgDao extends GenericDao<ImageBlob, Long> {
  public IconEntity getIconEntity(Long id);

  /**
   * Saves an icon entity - should only be used for form icons as this method updates the RSForm
   * table!
   *
   * @param iconEntity
   * @param updateRSFormTable whether RSForm table be also updated to point all parents to new icon
   * @return the saved icon entity
   */
  public IconEntity saveIconEntity(IconEntity iconEntity, boolean updateRSFormTable);

  public boolean updateIconRelation(long icon_id, long form_id);

  List<Long> getAllIconIds();
}
