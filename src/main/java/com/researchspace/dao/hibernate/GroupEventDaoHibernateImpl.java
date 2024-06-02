package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.GroupMembershipEventDao;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.events.GroupMembershipEvent;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class GroupEventDaoHibernateImpl extends GenericDaoHibernate<GroupMembershipEvent, Long>
    implements GroupMembershipEventDao {

  public GroupEventDaoHibernateImpl() {
    super(GroupMembershipEvent.class);
  }

  @Override
  public List<GroupMembershipEvent> getGroupEventsForUser(User toRetrieve) {
    return getSession()
        .createQuery("from GroupMembershipEvent where user = :user", GroupMembershipEvent.class)
        .setParameter("user", toRetrieve)
        .list();
  }

  @Override
  public List<GroupMembershipEvent> getGroupEventsForGroup(Group toRetrieve) {
    return getSession()
        .createQuery("from GroupMembershipEvent where group = :group", GroupMembershipEvent.class)
        .setParameter("group", toRetrieve)
        .list();
  }
}
