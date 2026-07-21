package com.researchspace.maintenance.dao;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.model.PaginationCriteria;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository("maintenanceDao")
public class MaintenanceDaoHibernate extends GenericDaoHibernate<ScheduledMaintenance, Long>
    implements MaintenanceDao {

  public MaintenanceDaoHibernate() {
    super(ScheduledMaintenance.class);
  }

  public MaintenanceDaoHibernate(Class<ScheduledMaintenance> persistentClass) {
    super(persistentClass);
  }

  @Override
  public Optional<ScheduledMaintenance> getNextScheduledMaintenance() {
    PaginationCriteria<ScheduledMaintenance> pagination = new PaginationCriteria<>();
    pagination.setResultsPerPage(1);
    return Optional.ofNullable(getFutureMaintenances(pagination).getFirstResult());
  }

  @Override
  public List<ScheduledMaintenance> getAllFutureMaintenances() {
    return getAllFutureMaintenanceOrderedByDateAsc();
  }

  @Override
  public ISearchResults<ScheduledMaintenance> getFutureMaintenances(
      PaginationCriteria<ScheduledMaintenance> pagination) {
    Date now = new Date();
    long total =
        getSession()
            .createQuery(
                "select count(*) from ScheduledMaintenance where endDate > :now", Long.class)
            .setParameter("now", now)
            .uniqueResult();
    long firstResult = pagination.getPageNumber() * pagination.getResultsPerPage();
    if (firstResult > Integer.MAX_VALUE) {
      return new SearchResultsImpl<>(Collections.emptyList(), pagination, total);
    }
    List<ScheduledMaintenance> results =
        getSession()
            .createQuery(
                "from ScheduledMaintenance where endDate > :now order by startDate asc, id asc",
                ScheduledMaintenance.class)
            .setParameter("now", now)
            .setFirstResult((int) firstResult)
            .setMaxResults(pagination.getResultsPerPage())
            .list();
    return new SearchResultsImpl<>(results, pagination, total);
  }

  private List<ScheduledMaintenance> getAllFutureMaintenanceOrderedByDateAsc() {
    return getSession()
        .createQuery(
            "from ScheduledMaintenance where endDate > :now order by startDate asc",
            ScheduledMaintenance.class)
        .setParameter("now", new Date())
        .list();
  }

  @Override
  public List<ScheduledMaintenance> getOldMaintenances() {
    return getSession()
        .createQuery(
            "from ScheduledMaintenance where endDate <= :now order by startDate asc",
            ScheduledMaintenance.class)
        .setParameter("now", new Date())
        .list();
  }
}
