package com.researchspace.dao.hibernate;

import com.researchspace.dao.FormUsageDao;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.User;
import com.researchspace.model.record.FormUsage;
import com.researchspace.model.record.RSForm;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;

@Repository("templateUsageDao")
public class FormUsageDaoHibernate extends GenericDaoHibernate<FormUsage, Long>
    implements FormUsageDao {

  public FormUsageDaoHibernate() {
    super(FormUsage.class);
  }

  @Override
  public List<String> getMostPopularForms(
      int numDocuments, final int limit, RSForm toExclude, final User u) {
    Session session = getSessionFactory().getCurrentSession();

    StringBuffer mysql = new StringBuffer();
    mysql.append(
        "select t, count(t) as c, max(lut)  as latest from "
            + " (select formStableID as t,  lastUsedTimeInMillis as lut from FormUsage, RSForm"
            + " where formStableID=RSForm.stableID"
            + " and RSForm.publishingState='PUBLISHED' "
            + " and user_id=:user_id "
            + " order by lastUsedTimeInMillis "
            + " limit 100 ) as subselect "
            + " group by t "
            + " order by c desc, lut desc ");

    NativeQuery<?> query = session.createSQLQuery(mysql.toString());

    query.setParameter("user_id", u.getId());

    query.setMaxResults(limit);
    List result = query.list();

    List<String> rc = new ArrayList<String>();
    for (int i = 0; i < result.size(); i++) {
      Object[] row = (Object[]) result.get(i);

      String templateIDStr = (String) row[0];
      rc.add(templateIDStr);
    }

    return rc;
  }

  @Override
  public Optional<FormUsage> getMostRecentlyUsedFormForUser(final User u) {
    Session session = getSessionFactory().getCurrentSession();

    org.hibernate.query.Query q =
        session.createQuery(
            " select id,  lastUsedTimeInMillis from FormUsage fu"
                + " where fu.user.id= :userId"
                + " order by lastUsedTimeInMillis desc");
    q.setMaxResults(1);
    q.setParameter("userId", u.getId());
    List<Object> result = q.list();

    if (!result.isEmpty()) {
      Object[] res = (Object[]) result.get(0);
      FormUsage t = get((Long) res[0]);
      return Optional.of(t);
    } else {
      return Optional.empty();
    }
  }
}
