package com.researchspace.service.impl;

import com.researchspace.dao.RecordUserFavoritesDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.BaseRecord.FavoritesStatus;
import com.researchspace.model.record.RecordUserFavorites;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.RecordFavoritesManager;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("recordFavorites")
public class RecordFavoritesManagerImpl implements RecordFavoritesManager {

  private static Logger log = LoggerFactory.getLogger(RecordFavoritesManagerImpl.class);

  private @Autowired RecordUserFavoritesDao recordUserFavoritesDao;
  private @Autowired BaseRecordManager baseRecordManager;
  private @Autowired UserDao userDao;

  public RecordUserFavorites get(Long recordId, Long userId) {
    return recordUserFavoritesDao.get(recordId, userId);
  }

  public List<BaseRecord> getFavoriteRecordsByUser(Long userId) {
    return recordUserFavoritesDao.getFavoriteRecordsByUser(userId);
  }

  public boolean isFavoriteRecordBy(Long recordId, Long userId) {
    return recordUserFavoritesDao.isFavoriteRecordBy(recordId, userId);
  }

  public void saveFavoriteRecord(Long recordId, Long userId) {
    if (!isFavoriteRecordBy(recordId, userId)) {
      User user = userDao.load(userId);
      BaseRecord baseRecord = baseRecordManager.load(recordId);
      RecordUserFavorites rus = new RecordUserFavorites(user, baseRecord);
      recordUserFavoritesDao.save(rus);
    }
  }

  public void deleteFavoriteRecord(Long recordId, Long userId) {
    if (isFavoriteRecordBy(recordId, userId)) {
      RecordUserFavorites rus = recordUserFavoritesDao.get(recordId, userId);
      recordUserFavoritesDao.remove(rus.getId());
    }
  }

  @Override
  public void updateTransientFavoriteStatus(Collection<BaseRecord> records, User subject) {
    List<Long> ids = records.stream().map(BaseRecord::getId).collect(Collectors.toList());
    List<Long> favoriteIds = recordUserFavoritesDao.findFavorites(ids, subject);
    for (BaseRecord br : records) {
      if (favoriteIds.contains(br.getId())) {
        br.setFavoriteStatus(FavoritesStatus.FAVORITE);
      } else {
        br.setFavoriteStatus(FavoritesStatus.NO_FAVORITE);
      }
    }
  }

  @Override
  public void deleteFavorites(Long recordId, User user) {
    BaseRecord baseRecord = baseRecordManager.get(recordId, user, true);
    deleteFavoriteRecord(recordId, user.getId());
    if (baseRecord.isFolder() || baseRecord.isNotebook()) {
      for (BaseRecord children : baseRecord.getChildrens()) {
        deleteFavorites(children.getId(), user);
      }
    }
  }
}
