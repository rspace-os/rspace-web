package com.researchspace.dao;

import com.researchspace.model.CollabGroupCreationTracker;
import com.researchspace.model.Group;
import com.researchspace.model.comms.MessageOrRequest;

/** DAO for retrieving CollabGroupCreationTracker objects. */
public interface CollaborationGroupTrackerDao extends GenericDao<CollabGroupCreationTracker, Long> {

  CollabGroupCreationTracker getByRequestId(MessageOrRequest mor);

  CollabGroupCreationTracker getByGroupId(Group group);
}
