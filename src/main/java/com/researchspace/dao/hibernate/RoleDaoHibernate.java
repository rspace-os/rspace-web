package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.RoleDao;
import com.researchspace.model.Role;
import org.hibernate.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class RoleDaoHibernate extends GenericDaoHibernate<Role, Long> implements RoleDao {

  /** Constructor to create a Generics-based version using Role as the entity */
  public RoleDaoHibernate() {
    super(Role.class);
  }

  /** {@inheritDoc} */
  public Role getRoleByName(String rolename) {
    Query<Role> q = getSession().createQuery("from Role where name=:rolename", Role.class);
    q.setParameter("rolename", rolename);
    return getFirstResultOrNull(q);
  }

  /** {@inheritDoc} */
  public void removeRole(String rolename) {
    Object role = getRoleByName(rolename);
    getSession().delete(role);
  }
}
