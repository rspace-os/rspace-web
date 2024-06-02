package com.researchspace.dao;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.events.GroupMembershipEvent;
import java.util.List;

/** Query group membership event table, RSPAC-683 */
public interface GroupMembershipEventDao extends GenericDao<GroupMembershipEvent, Long> {

  /** Gets GroupMembershipEvent for user */
  List<GroupMembershipEvent> getGroupEventsForUser(User toRetrieve);

  /** Gets GroupMembershipEvents for a group */
  List<GroupMembershipEvent> getGroupEventsForGroup(Group toRetrieve);
}
