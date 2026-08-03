package com.researchspace.maintenance.service;

import com.researchspace.maintenance.dao.MaintenanceDao;
import com.researchspace.maintenance.model.ApiV2MaintenanceResource;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.impl.AbstractCollectionManager;
import java.util.List;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.stereotype.Service;

/**
 * Manages scheduled maintenance and publishes successful changes so the derived next-maintenance
 * cache can be invalidated.
 */
@Service("maintenanceManager")
public class MaintenanceManager extends AbstractCollectionManager<ScheduledMaintenance, Long> {

  private final MaintenanceDao maintenanceDao;
  private final ApplicationEventPublisher events;
  private final MessageSourceUtils messages;

  public MaintenanceManager(
      MaintenanceDao maintenanceDao,
      ApplicationEventPublisher events,
      MessageSourceUtils messages) {
    super(maintenanceDao, ApiV2MaintenanceResource.DESCRIPTION);
    this.maintenanceDao = maintenanceDao;
    this.events = events;
    this.messages = messages;
  }

  /**
   * Retrieves maintenance object by id.
   *
   * @param id maintenance ID
   * @return maintenance with the given ID
   * @throws ObjectRetrievalFailureException if no object has the given ID
   */
  public ScheduledMaintenance getScheduledMaintenance(Long id) {
    return maintenanceDao.get(id);
  }

  /**
   * Gets all active or future scheduled maintenances, ordered by start date.
   *
   * @return matching maintenances, or an empty list
   */
  public List<ScheduledMaintenance> getAllFutureMaintenances() {
    return maintenanceDao.getAllFutureMaintenances();
  }

  /**
   * Gets old or expired maintenances, ordered by start date.
   *
   * @return matching maintenances, or an empty list
   */
  public List<ScheduledMaintenance> getOldMaintenances() {
    return maintenanceDao.getOldMaintenances();
  }

  /**
   * Retrieves the nearest scheduled maintenance, which may already be active.
   *
   * @return the nearest maintenance, or {@link ScheduledMaintenance#NULL} if none exists
   */
  @Cacheable(cacheNames = MaintenanceCacheInvalidator.CACHE_NAME)
  public ScheduledMaintenance getNextScheduledMaintenance() {
    return maintenanceDao.getNextScheduledMaintenance().orElse(ScheduledMaintenance.NULL);
  }

  /**
   * Inserts or updates maintenance. Can only be called by a sysadmin.
   *
   * @param maintenance maintenance to save
   * @param user authenticated user
   * @return saved maintenance
   * @throws AuthorizationException if the user is not a sysadmin
   */
  public ScheduledMaintenance saveScheduledMaintenance(
      ScheduledMaintenance maintenance, User user) {
    return createResource(maintenance, user);
  }

  /**
   * Removes a maintenance by ID. Can only be called by a sysadmin.
   *
   * @param id maintenance ID
   * @param user authenticated user
   * @throws AuthorizationException if the user is not a sysadmin
   */
  public void removeScheduledMaintenance(Long id, User user) {
    authorizeMutation(user);
    maintenanceDao.remove(id);
    resourcesChanged();
  }

  @Override
  protected void authorizeMutation(User actor) {
    if (!actor.hasRole(Role.SYSTEM_ROLE)) {
      throw new AuthorizationException(
          messages.getMessage("errors.authorization.maintenanceSysadminOnly"));
    }
  }

  @Override
  protected void validateResource(ScheduledMaintenance maintenance) {
    if (!maintenance.hasValidWindow()) {
      throw new MaintenanceOperationException(MaintenanceOperationException.Reason.INVALID_WINDOW);
    }
  }

  @Override
  protected Long getId(ScheduledMaintenance maintenance) {
    return maintenance.getId();
  }

  @Override
  protected void resourcesChanged() {
    events.publishEvent(new MaintenanceChangedEvent());
  }
}
