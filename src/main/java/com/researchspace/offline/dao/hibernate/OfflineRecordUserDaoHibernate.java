package com.researchspace.offline.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.offline.dao.OfflineRecordUserDao;
import com.researchspace.offline.model.OfflineRecordUser;
import com.researchspace.offline.model.OfflineWorkType;
import java.util.Date;
import java.util.List;
import org.hibernate.Session;
import org.springframework.stereotype.Repository;

@Repository("offlineRecordUserDao")
public class OfflineRecordUserDaoHibernate extends GenericDaoHibernate<OfflineRecordUser, Long>
    implements OfflineRecordUserDao {

  public OfflineRecordUserDaoHibernate() {
    super(OfflineRecordUser.class);
  }

  public OfflineRecordUserDaoHibernate(Class<OfflineRecordUser> persistentClass) {
    super(persistentClass);
  }

  @Override
  /**
   * This method ensures uniqueness of record edit lock, by locking record first for VIEW and then
   * updating to EDIT only if there is no other edit lock.
   *
   * <p>Even if EDIT lock for some reason won't be unique for record (depending on db transaction
   * model) the multiple edit locks should be eventually (after re-saving) downgraded to view locks
   */
  public OfflineRecordUser save(OfflineRecordUser object) {

    boolean afterSavingTryUpdateToEditLock = false;

    if (OfflineWorkType.EDIT.equals(object.getWorkType())) {
      object.setWorkType(OfflineWorkType.VIEW);
      afterSavingTryUpdateToEditLock = true;
    }

    OfflineRecordUser savedRecord = super.save(object);

    if (afterSavingTryUpdateToEditLock) {
      updateViewLockToEditLock(savedRecord);
    }

    return savedRecord;
  }

  private void updateViewLockToEditLock(OfflineRecordUser offlineRecordUser) {
    // run atomic update that will change view to edit if there is no edit lock for the record

    Session session = sessionFactory.getCurrentSession();
    // note that this is SQL not HQL Query, that's because of problems with hql inner query
    int updatedRows =
        session
            .createSQLQuery(
                "update OfflineRecordUser set workType=:editWorkType  where id=:id    and 1 >"
                    + " (select count(*) from (select record_id from OfflineRecordUser             "
                    + " where record_id=:recordId and workType=:editWorkType) as tmptable)")
            .setParameter("id", offlineRecordUser.getId())
            .setParameter("recordId", offlineRecordUser.getRecord().getId())
            .setParameter("editWorkType", OfflineWorkType.EDIT.ordinal())
            .executeUpdate();

    log.debug("offline work rows updated to edit lock: " + updatedRows);

    // re-reads state of the record from db, so work type is updated
    session.refresh(offlineRecordUser);
  }

  @SuppressWarnings("unchecked")
  @Override
  public OfflineRecordUser getOfflineWork(Long recordId, Long userId) {
    Session session = sessionFactory.getCurrentSession();
    List<OfflineRecordUser> result =
        (List<OfflineRecordUser>)
            session
                .createQuery("from OfflineRecordUser where record.id=:recordId and user.id=:userId")
                .setParameter("recordId", recordId)
                .setParameter("userId", userId)
                .list();
    if (result.size() == 0) {
      return null;
    }
    return ((List<OfflineRecordUser>) result).get(0);
  }

  @Override
  public OfflineRecordUser createOfflineWork(
      BaseRecord record, User user, OfflineWorkType workType) {
    OfflineRecordUser newOfflineWork = new OfflineRecordUser(record, user);
    newOfflineWork.setCreationDate(new Date());
    newOfflineWork.setWorkType(workType);

    return save(newOfflineWork);
  }

  @Override
  public void removeOfflineWork(Long recordId, User user) {
    Session session = sessionFactory.getCurrentSession();
    session
        .createQuery("delete from OfflineRecordUser where record.id=:recordId and user.id=:userId")
        .setParameter("recordId", recordId)
        .setParameter("userId", user.getId())
        .executeUpdate();
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<OfflineRecordUser> getOfflineWorkForRecord(BaseRecord record) {
    Session session = sessionFactory.getCurrentSession();
    Object result =
        session
            .createQuery("from OfflineRecordUser where record.id=:recordId")
            .setParameter("recordId", record.getId())
            .list();
    return (List<OfflineRecordUser>) result;
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<OfflineRecordUser> getOfflineWorkForUser(User user) {
    Session session = sessionFactory.getCurrentSession();
    Object result =
        session
            .createQuery(
                "select oru from OfflineRecordUser as oru"
                    + " join oru.record as record "
                    + " where record.deleted = false and oru.user.id=:userId")
            .setParameter("userId", user.getId())
            .list();
    return (List<OfflineRecordUser>) result;
  }
}
