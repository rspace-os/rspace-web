package com.researchspace.comms;

import static com.researchspace.comms.CommunicationTargetFinderPolicy.usersWithAnonymousRemoved;
import static com.researchspace.core.util.TransformerUtils.toSet;

import com.researchspace.dao.RecordGroupSharingDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.AbstractUserOrGroupImpl;
import com.researchspace.model.Group;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.permissions.RecordPermissionAdapter;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.Record;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("strictTargetFinderPolicy")
public class StrictPermissionCheckingRecipientFinderPolicy
    implements CommunicationTargetFinderPolicy {

  @Autowired private RecordGroupSharingDao groupShareDao;

  @Autowired private UserDao userDao;

  /**
   * If record is not null, will check subject's groups to see who has permission to view the
   * record; else will return a set of all users in the subject's groups as potential recipients.
   */
  @Override
  public Set<User> findPotentialTargetsFor(
      MessageType messageType, Record record, String searchTerm, User subject) {

    Set<User> targets = new HashSet<User>();
    targets.add(subject); // RSPAC-845
    if (record != null) {

      // make sure lab group PIs are added in even if record not
      // explicitly shared (unless sender themselves is a PI)//RSPAC-697.
      for (Group grp : subject.getGroups()) {
        if (grp.isLabGroup() && !subject.hasRole(Role.PI_ROLE)) {
          targets.addAll(grp.getPiusers());
          targets.addAll(grp.getLabAdminsWithViewAllPermission());
        }
      }
      List<AbstractUserOrGroupImpl> explicitlySharedUsersGroups =
          groupShareDao.getUsersOrGroupsWithRecordAccess(record.getId());
      if (record.isNotebookEntry()) { // RSPAC-873
        Notebook nb = record.asStrucDoc().getParentNotebook();
        explicitlySharedUsersGroups.addAll(
            groupShareDao.getUsersOrGroupsWithRecordAccess(nb.getId()));
      }
      // may be null if is general message
      PermissionType action = getRequiredPermissionForMessageType(messageType);

      RecordPermissionAdapter rpa = new RecordPermissionAdapter(record);
      rpa.setActions(action);
      rpa.setDomain(PermissionDomain.RECORD);

      for (AbstractUserOrGroupImpl gp : explicitlySharedUsersGroups) {
        Set<User> groupMembers;
        if (gp.isGroup()) {
          groupMembers = gp.asGroup().getMembers();
        } else {
          groupMembers = toSet(gp.asUser());
        }
        for (User grpMember : groupMembers) {
          if (action != null) {
            if (!targets.contains(grpMember)
                && isPermitted(record, action, rpa, grpMember)
                && grpMember.isEnabled()) {
              targets.add(grpMember);
            }
          } else if (grpMember.isEnabled()) {
            targets.add(grpMember);
          }
        }
      }
      // this doesn't need a record permission test, just add other members of our groups
      // as candidates
    } else {
      Set<Group> groups = subject.getGroups();
      for (Group grp : groups) {
        for (User user : grp.getMembers()) {
          if (user.isEnabled()) {
            targets.add(user);
          }
        }
      }
    }
    // RSPAC-845
    if (!MessageType.REQUEST_RECORD_REVIEW.equals(messageType)) {
      targets.remove(subject); // don't need to send message to self unless review record
    }

    if (searchTerm != null) {
      List<User> matchingUsers = userDao.searchUsers(searchTerm);
      targets.retainAll(matchingUsers);
    }

    return usersWithAnonymousRemoved(targets);
  }

  private boolean isPermitted(
      Record record, PermissionType action, RecordPermissionAdapter rpa, User grpMember) {
    return grpMember.isPermitted(rpa, true)
        || record.getSharingACL().isPermitted(grpMember, action);
  }

  private PermissionType getRequiredPermissionForMessageType(MessageType messageType) {
    if (MessageType.REQUEST_RECORD_REVIEW.equals(messageType)) {
      return PermissionType.WRITE;
    }
    if (MessageType.REQUEST_RECORD_WITNESS.equals(messageType)) {
      return PermissionType.READ;
    }
    return null;
  }

  @Override
  public String getFailureMessageIfUserInvalidTarget() {
    return " Intended recipient is not authorized to receive this message";
  }
}
