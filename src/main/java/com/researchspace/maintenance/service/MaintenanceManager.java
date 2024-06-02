package com.researchspace.maintenance.service;

import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.model.User;
import java.util.Collections;
import java.util.List;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.orm.ObjectRetrievalFailureException;

/** For managing scheduled maintenance periods. */
public interface MaintenanceManager {

  /**
   * Retrieves maintenance object by id.
   *
   * @param id
   * @return {@link ScheduledMaintenance} with given id
   * @throws ObjectRetrievalFailureException if no object with given id
   */
  ScheduledMaintenance getScheduledMaintenance(Long id);

  /**
   * Get all active or future scheduled maintenances, ordered by startDate.
   *
   * @return List of {@link ScheduledMaintenance} or empty list
   */
  List<ScheduledMaintenance> getAllFutureMaintenances();

  /**
   * Get old or expired maintenances, ordered by startDate.
   *
   * @return List of {@link ScheduledMaintenance} or empty list
   */
  default List<ScheduledMaintenance> getOldMaintenances() {
    return Collections.emptyList();
  }

  /**
   * Retrieves nearest scheduled maintenance (may be already active), if there is any.
   *
   * @param maintenance
   * @return {@link ScheduledMaintenance} or ScheduledMaintenance.NULL if none found
   */
  ScheduledMaintenance getNextScheduledMaintenance();

  /**
   * Inserts or updates maintenance object. Can be only called by user with sysadmin role.
   *
   * @param maintenance
   * @param user
   * @return saved {@link ScheduledMaintenance} object
   * @throws AuthorizationException if user doesn't have sysadmin role
   */
  ScheduledMaintenance saveScheduledMaintenance(ScheduledMaintenance maintenance, User user);

  /**
   * Removes maintenance objecdt by id. Can be only called by user with sysadmin role.
   *
   * @param id
   * @param sysUser
   * @throws AuthorizationException if user doesn't have sysadmin role
   */
  void removeScheduledMaintenance(Long id, User user);
}
