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
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;
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
  public SampleRequest getForUpdate(Long id) {
    // a query rather than get(), so pending changes are flushed before the row is locked
    return sessionFactory
        .getCurrentSession()
        .createQuery("from SampleRequest where id = :id", SampleRequest.class)
        .setParameter("id", id)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .uniqueResult();
  }

  @Override
  public ISearchResults<SampleRequest> getRequestsForUser(
      PaginationCriteria<SampleRequest> pgCrit,
      SampleRequestRole role,
      Set<SampleRequestStatus> statuses,
      Long sampleId,
      User user) {

    boolean hasStatusFilter = CollectionUtils.isNotEmpty(statuses);
    // a deleted sample must not drive a pending count; the request row itself is kept for history
    String where = " where " + roleClause(role) + " and req.sample.deleted = false";
    if (hasStatusFilter) {
      where += " and req.status in (:statuses)";
    }
    if (sampleId != null) {
      where += " and req.sample.id = :sampleId";
    }

    Long total =
        bind(
                role,
                sessionFactory
                    .getCurrentSession()
                    .createQuery("select count(req) from SampleRequest req" + where, Long.class),
                hasStatusFilter ? statuses : null,
                sampleId,
                user)
            .uniqueResult();

    List<SampleRequest> page =
        bind(
                role,
                sessionFactory
                    .getCurrentSession()
                    .createQuery(
                        "from SampleRequest req" + where + " order by req.created desc",
                        SampleRequest.class),
                hasStatusFilter ? statuses : null,
                sampleId,
                user)
            .setFirstResult(pgCrit.getFirstResultIndex())
            .setMaxResults(pgCrit.getResultsPerPage())
            .list();

    return new SearchResultsImpl<>(page, pgCrit, total.intValue());
  }

  /**
   * The approver is never stored, so the owner side joins through the sample's current owner. A
   * null role means no filtering by role: the user is either the requester or the owner.
   */
  private String roleClause(SampleRequestRole role) {
    if (role == null) {
      return "(req.requester = :user or req.sample.owner = :user)";
    }
    switch (role) {
      case REQUESTER:
        return "req.requesterUsername = :username";
      case OWNER:
        return "req.sample.owner = :user";
      default:
        throw new UnsupportedOperationException("Unhandled sample request role: " + role);
    }
  }

  private <T> org.hibernate.query.Query<T> bind(
      SampleRequestRole role,
      org.hibernate.query.Query<T> query,
      Set<SampleRequestStatus> statuses,
      Long sampleId,
      User user) {
    // only the clause for the requested role is in the query, so bind only its parameter
    if (SampleRequestRole.REQUESTER.equals(role)) {
      query.setParameter("username", user.getUsername());
    } else {
      query.setParameter("user", user);
    }
    if (CollectionUtils.isNotEmpty(statuses)) {
      query.setParameterList("statuses", statuses);
    }
    if (sampleId != null) {
      query.setParameter("sampleId", sampleId);
    }
    return query;
  }
}
