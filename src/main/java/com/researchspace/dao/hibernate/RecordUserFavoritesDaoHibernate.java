package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.RecordUserFavoritesDao;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.RecordUserFavorites;
import java.util.Collections;
import java.util.List;
import org.hibernate.query.Query;
import org.springframework.stereotype.Repository;

@Repository("recordUserFavoritesDao")
public class RecordUserFavoritesDaoHibernate extends GenericDaoHibernate<RecordUserFavorites, Long>
    implements RecordUserFavoritesDao {

  /** Constructor that sets the entity to RecordUserFavorites.class. */
  public RecordUserFavoritesDaoHibernate() {
    super(RecordUserFavorites.class);
  }

  public RecordUserFavoritesDaoHibernate(Class<RecordUserFavorites> persistentClass) {
    super(persistentClass);
  }

  @Override
  public RecordUserFavorites get(Long recordId, Long userId) {
    Query<RecordUserFavorites> query =
        getSession()
            .createQuery(
                "from RecordUserFavorites where user.id=:userId " + "and record.id=:recordId ",
                RecordUserFavorites.class)
            .setParameter("userId", userId)
            .setParameter("recordId", recordId);
    RecordUserFavorites result = query.uniqueResult();
    if (result == null) {
      throw new RuntimeException("Unable to find RecordUserFavorites");
    }
    return result;
  }

  @Override
  public List<BaseRecord> getFavoriteRecordsByUser(Long userId) {
    Query<BaseRecord> query =
        getSession()
            .createQuery(
                "select distinct rus.record from RecordUserFavorites rus "
                    + "where rus.user.id=:userId",
                BaseRecord.class);
    query.setParameter("userId", userId);
    List<BaseRecord> result = query.list();
    return result;
  }

  public boolean isFavoriteRecordBy(Long recordId, Long userId) {
    Query<Long> query =
        getSession()
            .createQuery(
                "select rus.id from RecordUserFavorites rus "
                    + "where rus.user.id=:userId and rus.record.id=:recordId",
                Long.class)
            .setParameter("userId", userId)
            .setParameter("recordId", recordId);
    return query.list().size() > 0;
  }

  @Override
  public List<Long> findFavorites(List<Long> recordIds, User subject) {
    if (recordIds.isEmpty()) {
      return Collections.emptyList();
    }
    return getSession()
        .createQuery(
            "select rus.record.id from RecordUserFavorites rus "
                + "where rus.user.id=:userId and rus.record.id in (:recordId)",
            Long.class)
        .setParameter("userId", subject.getId())
        .setParameterList("recordId", recordIds)
        .list();
  }
}
