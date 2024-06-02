package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.UserAccountEventDao;
import com.researchspace.model.User;
import com.researchspace.model.events.UserAccountEvent;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository("userAccountEventDao")
public class UserAccountEventDaoHibernateImpl extends GenericDaoHibernate<UserAccountEvent, String>
    implements UserAccountEventDao {

  public UserAccountEventDaoHibernateImpl() {
    super(UserAccountEvent.class);
  }

  @Override
  public List<UserAccountEvent> getAccountEventsForUser(User toRetrieve) {
    return getSession()
        .createQuery("from UserAccountEvent where user = :user", UserAccountEvent.class)
        .setParameter("user", toRetrieve)
        .list();
  }
}
