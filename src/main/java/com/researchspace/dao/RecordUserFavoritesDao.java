package com.researchspace.dao;

import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.RecordUserFavorites;
import java.util.List;

public interface RecordUserFavoritesDao extends GenericDao<RecordUserFavorites, Long> {

  /**
   * Get RecordUserFavorites object.
   *
   * @param recordId
   * @param userId
   * @return
   */
  RecordUserFavorites get(Long recordId, Long userId);

  /**
   * Get a list of Favorite BaseRecords by a specific user.
   *
   * @param userId
   * @return
   */
  List<BaseRecord> getFavoriteRecordsByUser(Long userId);

  /**
   * Check if a base record is favorite by user.
   *
   * @param recordId
   * @param userId
   * @return
   */
  boolean isFavoriteRecordBy(Long recordId, Long userId);

  /**
   * Given a list of BaseRecord Ids, returns those ids which are favourites
   *
   * @param records
   * @param subject
   * @return A list of Record ids of favorite records (might be empty)
   * @see https://ops.researchspace.com/globalId/SD4536 for query analysis
   */
  List<Long> findFavorites(List<Long> records, User subject);
}
