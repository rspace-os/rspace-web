package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.UserApiKeyDao;
import com.researchspace.model.User;
import com.researchspace.model.UserApiKey;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository("userApiKey")
public class UserApiKeyDaoHibernate extends GenericDaoHibernate<UserApiKey, Long>
    implements UserApiKeyDao {

  public UserApiKeyDaoHibernate() {
    super(UserApiKey.class);
  }

  @Override
  public int deleteForUser(User user) {
    return getSession()
        .createQuery("delete from UserApiKey key where key.user.id =:userId ")
        .setParameter("userId", user.getId())
        .executeUpdate();
  }

  @Override
  public Optional<UserApiKey> getKeyForUser(User user) {
    UserApiKey rc =
        (UserApiKey)
            getSession()
                .createQuery("from UserApiKey key where key.user.id =:userId ")
                .setParameter("userId", user.getId())
                .uniqueResult();
    return Optional.ofNullable(rc);
  }
}
