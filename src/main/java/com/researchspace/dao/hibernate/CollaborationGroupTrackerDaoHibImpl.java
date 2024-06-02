package com.researchspace.dao.hibernate;

import com.researchspace.dao.CollaborationGroupTrackerDao;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.CollabGroupCreationTracker;
import com.researchspace.model.Group;
import com.researchspace.model.comms.MessageOrRequest;
import org.springframework.stereotype.Repository;

@Repository("collabGroupTracker")
public class CollaborationGroupTrackerDaoHibImpl
    extends GenericDaoHibernate<CollabGroupCreationTracker, Long>
    implements CollaborationGroupTrackerDao {

  public CollaborationGroupTrackerDaoHibImpl() {
    super(CollabGroupCreationTracker.class);
  }

  @Override
  public CollabGroupCreationTracker getByRequestId(MessageOrRequest mor) {
    return getSession()
        .createQuery(
            "from CollabGroupCreationTracker where mor.id=:morid", CollabGroupCreationTracker.class)
        .setParameter("morid", mor.getId())
        .uniqueResult();
  }

  @Override
  public CollabGroupCreationTracker getByGroupId(Group group) {
    return getSession()
        .createQuery(
            "from CollabGroupCreationTracker where group.id=:groupid",
            CollabGroupCreationTracker.class)
        .setParameter("groupid", group.getId())
        .uniqueResult();
  }
}
