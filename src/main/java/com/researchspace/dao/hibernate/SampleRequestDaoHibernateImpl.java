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
    // requests against a trashed sample drop out of listings, but stay readable by id
    String where = " where " + roleClause(role) + " and req.sample.deleted = false";
    if (hasStatusFilter) {
      where += " and req.status in (:statuses)";
    }
    if (sampleId != null) {
      where += " and req.sample.id = :sampleId";
    }

    Long total =
        bind(
                sessionFactory
                    .getCurrentSession()
                    .createQuery("select count(req) from SampleRequest req" + where, Long.class),
                statuses,
                sampleId,
                user)
            .uniqueResult();

    // creation date is the only sort key the API accepts
    String dir = pgCrit.getSortOrder().toString();
    List<SampleRequest> page =
        bind(
                sessionFactory
                    .getCurrentSession()
                    .createQuery(
                        // fetched with the page: every row's sample is shown
                        "from SampleRequest req join fetch req.sample"
                            + where
                            + " order by req.created "
                            + dir
                            + ", req.id "
                            + dir,
                        SampleRequest.class),
                statuses,
                sampleId,
                user)
            .setFirstResult(pgCrit.getFirstResultIndex())
            .setMaxResults(pgCrit.getResultsPerPage())
            .list();

    return new SearchResultsImpl<>(page, pgCrit, total.intValue());
  }

  /**
   * OWNER follows originalOwner rather than the sample's current owner, so a request stays with
   * whoever was asked even after a transfer. A null role means either side.
   */
  private String roleClause(SampleRequestRole role) {
    // ids, not usernames, so a later account with a deleted user's username does not inherit them
    return role == null
        ? "(req.requesterId = :userId or req.originalOwnerId = :userId)"
        : switch (role) {
          case REQUESTER -> "req.requesterId = :userId";
          case OWNER -> "req.originalOwnerId = :userId";
        };
  }

  @Override
  public List<SampleRequest> getActiveRequestsForSample(Long sampleId) {
    // the transfer's owner change locks the sample first, so a request being raised (which
    // share-locks it) finishes before these rows are locked, rather than deadlocking
    sessionFactory.getCurrentSession().flush();
    return sessionFactory
        .getCurrentSession()
        .createQuery(
            "from SampleRequest req where req.sample.id = :sampleId"
                + " and req.status in (:statuses)",
            SampleRequest.class)
        .setParameter("sampleId", sampleId)
        .setParameterList(
            "statuses", List.of(SampleRequestStatus.PENDING, SampleRequestStatus.APPROVED))
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .list();
  }

  private <T> org.hibernate.query.Query<T> bind(
      org.hibernate.query.Query<T> query,
      Set<SampleRequestStatus> statuses,
      Long sampleId,
      User user) {
    query.setParameter("userId", user.getId());
    if (CollectionUtils.isNotEmpty(statuses)) {
      query.setParameterList("statuses", statuses);
    }
    if (sampleId != null) {
      query.setParameter("sampleId", sampleId);
    }
    return query;
  }
}
