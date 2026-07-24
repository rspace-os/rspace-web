package com.researchspace.maintenance.dao;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.dao.GenericDao;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.model.PaginationCriteria;
import java.util.List;
import java.util.Optional;

public interface MaintenanceDao extends GenericDao<ScheduledMaintenance, Long> {

  /**
   * Finds scheduled maintenance that is either active now, or is closest in the future
   *
   * @return Optional closest {@link ScheduledMaintenance} as is null if there is no scheduled
   *     maintenance.
   */
  Optional<ScheduledMaintenance> getNextScheduledMaintenance();

  /**
   * Get all active or future scheduled maintenances, ordered by startDate.
   *
   * @return List of {@link ScheduledMaintenance} or empty list
   */
  List<ScheduledMaintenance> getAllFutureMaintenances();

  /** Returns one database-backed page of active or future maintenances. */
  ISearchResults<ScheduledMaintenance> getFutureMaintenances(
      PaginationCriteria<ScheduledMaintenance> pagination);

  /**
   * Get all expired scheduled maintenances, ordered by startDate.
   *
   * @return List of {@link ScheduledMaintenance} or empty list
   */
  List<ScheduledMaintenance> getOldMaintenances();
}
