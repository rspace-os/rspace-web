package com.researchspace.maintenance.dao;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.SimpleExpression;
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
    return doList(Restrictions.gt("endDate", new Date()));
  }

  @Override
  public List<ScheduledMaintenance> getOldMaintenances() {
    return doList(Restrictions.le("endDate", new Date()));
  }

  @SuppressWarnings("unchecked")
  private List<ScheduledMaintenance> doList(SimpleExpression dateRestriction) {
    return (List<ScheduledMaintenance>)
        getSession()
            .createCriteria(ScheduledMaintenance.class)
            .add(dateRestriction)
            .addOrder(Order.asc("startDate"))
            .list();
  }
}
