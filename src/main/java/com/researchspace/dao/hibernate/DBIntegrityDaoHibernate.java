package com.researchspace.dao.hibernate;

import com.researchspace.dao.DBIntegrityDAO;
import com.researchspace.model.record.BaseRecord;
import java.util.List;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DBIntegrityDaoHibernate implements DBIntegrityDAO {

  @Autowired private SessionFactory sessionFactory;

  @Override
  public List<BaseRecord> getOrphanedRecords() {
    String query = "select br from BaseRecord br where br.owner is null";
    return sessionFactory.getCurrentSession().createQuery(query, BaseRecord.class).list();
  }

  @Override
  public List<Long> getTemporaryFavouriteDocs() {
    String query =
        "select rf.id from RecordUserFavorites rf   where  rf.record.id in (select r.tempRecord.id"
            + " from Record r)";
    return sessionFactory.getCurrentSession().createQuery(query, Long.class).list();
  }
}
