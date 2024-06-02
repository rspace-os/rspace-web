package com.researchspace.dao;

import com.researchspace.model.Thumbnail;
import com.researchspace.model.Thumbnail.SourceType;
import java.util.List;

public interface ThumbnailDao extends GenericDao<Thumbnail, Long> {
  /**
   * Queries by non-null properties of this example
   *
   * @param example
   * @return a {@link Thumbnail}, or <code>null</code> if there is not a thumbnail with these
   *     properties in the DB.
   */
  Thumbnail getThumbnail(Thumbnail example);

  int deleteAllThumbnails(SourceType type, Long sourceId);

  int deleteAllThumbnails(SourceType type, Long sourceId, Long sourceParentId);

  int deleteAllThumbnails(SourceType type, Long sourceId, Long sourceParentId, Long revisionId);

  /**
   * Get all thumbnails associated with a field
   *
   * @return
   */
  List<Thumbnail> getByFieldId(Long fieldId);
}
