package com.researchspace.maintenance.service;

import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.model.User;
import com.researchspace.service.CollectionManager;
import java.util.List;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.orm.ObjectRetrievalFailureException;

/** Manages scheduled maintenance and its REST API v2 collection operations. */
public interface MaintenanceManager extends CollectionManager<ScheduledMaintenance, Long> {

  /**
   * Retrieves a scheduled maintenance entry by ID.
   *
   * @param id maintenance ID
   * @return maintenance with the specified ID
   * @throws ObjectRetrievalFailureException if no object has the specified ID
   */
  ScheduledMaintenance getScheduledMaintenance(Long id);

  /**
   * Gets all active or future scheduled maintenance entries, ordered by start date.
   *
   * @return matching maintenance, or an empty list
   */
  List<ScheduledMaintenance> getAllFutureMaintenances();

  /**
   * Gets all expired scheduled maintenance entries, ordered by start date.
   *
   * @return matching maintenance, or an empty list
   */
  List<ScheduledMaintenance> getOldMaintenances();

  /**
   * Retrieves the nearest scheduled maintenance entry. The entry can already be active.
   *
   * @return the nearest maintenance, or {@link ScheduledMaintenance#NULL} if none exists
   */
  ScheduledMaintenance getNextScheduledMaintenance();

  /**
   * Inserts or updates a scheduled maintenance entry. Only a system administrator can call this
   * method.
   *
   * @param maintenance maintenance to save
   * @param user authenticated user
   * @return saved maintenance
   * @throws AuthorizationException if the user is not a system administrator
   */
  ScheduledMaintenance saveScheduledMaintenance(ScheduledMaintenance maintenance, User user);

  /**
   * Removes a scheduled maintenance entry by ID. Only a system administrator can call this method.
   *
   * @param id maintenance ID
   * @param user authenticated user
   * @throws AuthorizationException if the user is not a system administrator
   */
  void removeScheduledMaintenance(Long id, User user);
}
