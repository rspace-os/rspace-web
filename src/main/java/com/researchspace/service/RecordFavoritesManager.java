package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.RecordUserFavorites;
import java.util.Collection;
import java.util.List;

public interface RecordFavoritesManager {

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
   * @param recordId
   * @param userId
   */
  void saveFavoriteRecord(Long recordId, Long userId);

  /**
   * @param recordId
   * @param userId
   */
  void deleteFavoriteRecord(Long recordId, Long userId);

  /**
   * Given a list of BaseRecords, marks them as favourites if they are.
   *
   * @param records
   * @param subject
   */
  void updateTransientFavoriteStatus(Collection<BaseRecord> records, User subject);

  /**
   * Method to delete from favorites recursively. Called from /workspace/ajax/delete method.
   *
   * @param recordId
   * @param user
   */
  void deleteFavorites(Long recordId, User user);
}
