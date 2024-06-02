package com.researchspace.service.impl;

import com.researchspace.dao.RoleDao;
import com.researchspace.model.Role;
import com.researchspace.service.RoleManager;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Implementation of RoleManager interface. */
@Service("roleManager")
public class RoleManagerImpl extends GenericManagerImpl<Role, Long> implements RoleManager {
  RoleDao roleDao;

  @Autowired
  public RoleManagerImpl(RoleDao roleDao) {
    super(roleDao);
    this.roleDao = roleDao;
  }

  /** {@inheritDoc} */
  public List<Role> getRoles(Role role) {
    return dao.getAll();
  }

  /** {@inheritDoc} */
  public Role getRole(String rolename) {
    return roleDao.getRoleByName(rolename);
  }

  /** {@inheritDoc} */
  public Role saveRole(Role role) {
    return dao.save(role);
  }

  /** {@inheritDoc} */
  public void removeRole(String rolename) {
    roleDao.removeRole(rolename);
  }
}
