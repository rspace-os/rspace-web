package com.researchspace.dao.hibernate;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.SampleRequestDao;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SampleRequest;
import com.researchspace.model.inventory.SampleRequestRole;
import com.researchspace.model.inventory.SampleRequestStatus;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class SampleRequestDaoHibernateImpl extends GenericDaoHibernate<SampleRequest, Long>
    implements SampleRequestDao {

  public SampleRequestDaoHibernateImpl(Class<SampleRequest> persistentClass) {
    super(persistentClass);
  }

  public SampleRequestDaoHibernateImpl() {
    super(SampleRequest.class);
  }

  @Override
  public ISearchResults<SampleRequest> getRequestsForUser(
      PaginationCriteria<SampleRequest> pgCrit,
      SampleRequestRole role,
      SampleRequestStatus status,
      Long sampleId,
      User user) {

    // a deleted sample must not drive a pending count; the request row itself is kept for history
    String where = " where " + roleClause(role) + " and req.sample.deleted = false";
    if (status != null) {
      where += " and req.status = :status";
    }
    if (sampleId != null) {
      where += " and req.sample.id = :sampleId";
    }

    Long total =
        bind(
                sessionFactory
                    .getCurrentSession()
                    .createQuery("select count(req) from SampleRequest req" + where, Long.class),
                status,
                sampleId,
                user)
            .uniqueResult();

    List<SampleRequest> page =
        bind(
                sessionFactory
                    .getCurrentSession()
                    .createQuery(
                        "from SampleRequest req" + where + " order by req.created desc",
                        SampleRequest.class),
                status,
                sampleId,
                user)
            .setFirstResult(pgCrit.getFirstResultIndex())
            .setMaxResults(pgCrit.getResultsPerPage())
            .list();

    return new SearchResultsImpl<>(page, pgCrit, total.intValue());
  }

  /** The approver is never stored, so the owner side joins through the sample's current owner. */
  private String roleClause(SampleRequestRole role) {
    switch (role) {
      case REQUESTER:
        return "req.requester = :user";
      case OWNER:
        return "req.sample.owner = :user";
      default:
        throw new UnsupportedOperationException("Unhandled sample request role: " + role);
    }
  }

  private <T> org.hibernate.query.Query<T> bind(
      org.hibernate.query.Query<T> query, SampleRequestStatus status, Long sampleId, User user) {
    query.setParameter("user", user);
    if (status != null) {
      query.setParameter("status", status);
    }
    if (sampleId != null) {
      query.setParameter("sampleId", sampleId);
    }
    return query;
  }
}
