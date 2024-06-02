package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.UserKeyDao;
import com.researchspace.model.User;
import com.researchspace.model.UserKeyPair;
import org.springframework.stereotype.Repository;

@Repository("userKeyDao")
public class UserKeyDaoHibernate extends GenericDaoHibernate<UserKeyPair, Long>
    implements UserKeyDao {

  public UserKeyDaoHibernate() {
    super(UserKeyPair.class);
  }

  @Override
  public UserKeyPair getUserKeyPair(User user) {
    return getSession()
        .createQuery("from UserKeyPair where user.id=:userId", UserKeyPair.class)
        .setParameter("userId", user.getId())
        .uniqueResult();
  }
}
