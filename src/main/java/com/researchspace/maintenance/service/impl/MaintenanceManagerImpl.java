package com.researchspace.maintenance.service.impl;

import com.researchspace.maintenance.dao.MaintenanceDao;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.maintenance.service.MaintenanceManager;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import java.util.List;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Next scheduled maintenance is cached by Spring. <br>
 * Any operation that updates or creates a new ScheduledMaintenance should:
 *
 * <ul>
 *   <li>Ensure subject is a sysadmin
 *   <li>add a CacheEvict annotation
 * </ul>
 */
@Service("maintenanceManager")
@CacheConfig(cacheNames = "com.researchspace.maintenance.service.impl.Maintenance")
public class MaintenanceManagerImpl implements MaintenanceManager {

  private @Autowired MaintenanceDao maintenanceDao;

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
  @CacheEvict(allEntries = true)
  public ScheduledMaintenance saveScheduledMaintenance(
      ScheduledMaintenance maintenance, User user) {
    verifySysadminUser(user);
    return maintenanceDao.save(maintenance);
  }

  @Override
  @CacheEvict(allEntries = true)
  public void removeScheduledMaintenance(Long id, User user) {
    verifySysadminUser(user);
    maintenanceDao.remove(id);
  }

  @Override
  @Cacheable() // the key for this is the 'SimpleKey.EMPTY' as there is no key to use here
  public ScheduledMaintenance getNextScheduledMaintenance() {
    return maintenanceDao.getNextScheduledMaintenance().orElse(ScheduledMaintenance.NULL);
  }

  private void verifySysadminUser(User user) {
    if (!user.hasRole(Role.SYSTEM_ROLE)) {
      throw new AuthorizationException("Only sysadmin can manage scheduled maintenances");
    }
  }
}
