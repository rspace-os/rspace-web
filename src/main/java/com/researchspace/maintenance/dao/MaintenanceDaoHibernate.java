package com.researchspace.maintenance.dao;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.maintenance.model.ScheduledMaintenance;
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
    List<ScheduledMaintenance> allFutureMaintenances = getAllFutureMaintenanceOrderedByDateAsc();
    if (allFutureMaintenances.size() > 0) {
      return Optional.of(allFutureMaintenances.get(0));
    }
    return Optional.ofNullable(null);
  }

  @Override
  public List<ScheduledMaintenance> getAllFutureMaintenances() {
    return getAllFutureMaintenanceOrderedByDateAsc();
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
