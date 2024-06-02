package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.UserGroupDao;
import com.researchspace.model.UserGroup;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class UserGroupDaoHibernate extends GenericDaoHibernate<UserGroup, Long>
    implements UserGroupDao {

  public UserGroupDaoHibernate() {
    super(UserGroup.class);
  }

  @Override
  public List<UserGroup> findByUserId(Long userId) {
    return getSession()
        .createQuery("from UserGroup ug where ug.user.id=:id", UserGroup.class)
        .setParameter("id", userId)
        .list();
  }
}
