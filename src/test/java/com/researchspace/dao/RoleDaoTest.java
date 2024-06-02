package com.researchspace.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.researchspace.Constants;
import com.researchspace.model.Role;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class RoleDaoTest extends BaseDaoTestCase {
  @Autowired private RoleDao dao;

  @Test
  public void testGetRoleInvalid() throws Exception {
    Role role = dao.getRoleByName("badrolename");
    assertNull(role);
  }

  @Test
  public void testGetRole() throws Exception {
    Role role = dao.getRoleByName(Constants.USER_ROLE);
    assertNotNull(role);
  }

  @Test
  public void testUpdateRole() throws Exception {
    Role role = dao.getRoleByName("ROLE_USER");
    role.setDescription("test descr");
    dao.save(role);
    flush();

    role = dao.getRoleByName("ROLE_USER");
    assertEquals("test descr", role.getDescription());
  }

  @Test
  public void testAddAndRemoveRole() throws Exception {
    Role role = Role.PI_ROLE;
    role.setDescription("new role descr");
    dao.save(role);
    flush();

    role = dao.getRoleByName(role.getName());
    assertNotNull(role.getDescription());
  }
}
