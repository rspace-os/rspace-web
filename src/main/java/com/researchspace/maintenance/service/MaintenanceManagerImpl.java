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
import org.springframework.stereotype.Service;

/** Default scheduled-maintenance manager. */
@Service("maintenanceManager")
public class MaintenanceManagerImpl extends AbstractCollectionManager<ScheduledMaintenance, Long>
    implements MaintenanceManager {

  private final MaintenanceDao maintenanceDao;
  private final ApplicationEventPublisher events;
  private final MessageSourceUtils messages;

  public MaintenanceManagerImpl(
      MaintenanceDao maintenanceDao,
      ApplicationEventPublisher events,
      MessageSourceUtils messages) {
    super(
        maintenanceDao,
        ApiV2MaintenanceResource.DESCRIPTION,
        ApiV2MaintenanceResource.MUTATION_LIMITS);
    this.maintenanceDao = maintenanceDao;
    this.events = events;
    this.messages = messages;
  }

  @Override
  public ScheduledMaintenance getScheduledMaintenance(Long id) {
    return maintenanceDao.get(id);
  }

  @Override
  public List<ScheduledMaintenance> getAllFutureMaintenances() {
    return maintenanceDao.getAllFutureMaintenances();
  }

  @Override
  public List<ScheduledMaintenance> getOldMaintenances() {
    return maintenanceDao.getOldMaintenances();
  }

  @Override
  @Cacheable(cacheNames = MaintenanceCacheInvalidator.CACHE_NAME)
  public ScheduledMaintenance getNextScheduledMaintenance() {
    return maintenanceDao.getNextScheduledMaintenance().orElse(ScheduledMaintenance.NULL);
  }

  @Override
  public ScheduledMaintenance saveScheduledMaintenance(
      ScheduledMaintenance maintenance, User user) {
    return createResource(maintenance, user);
  }

  @Override
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
