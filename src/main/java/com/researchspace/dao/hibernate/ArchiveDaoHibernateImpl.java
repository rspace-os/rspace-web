package com.researchspace.dao.hibernate;

import com.researchspace.dao.ArchiveDao;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.ArchivalCheckSum;
import java.util.List;
import org.hibernate.query.Query;
import org.springframework.stereotype.Repository;

@Repository("archiceDao")
public class ArchiveDaoHibernateImpl extends GenericDaoHibernate<ArchivalCheckSum, String>
    implements ArchiveDao {

  public ArchiveDaoHibernateImpl() {
    super(ArchivalCheckSum.class);
  }

  public List<ArchivalCheckSum> getUnexpiredArchives() {
    Query<ArchivalCheckSum> query =
        getSession()
            .createQuery(
                " from ArchivalCheckSum where downloadTimeExpired = :expired",
                ArchivalCheckSum.class);
    query.setParameter("expired", false);
    return query.list();
  }
}
