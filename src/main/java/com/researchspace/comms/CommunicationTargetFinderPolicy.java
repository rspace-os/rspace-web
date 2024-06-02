package com.researchspace.comms;

import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.record.Record;
import java.util.Set;
import java.util.stream.Collectors;

/** Strategy interface for policies to find possible recipients of a message */
public interface CommunicationTargetFinderPolicy {
  static Set<User> usersWithAnonymousRemoved(Set<User> targets) {
    return targets.stream()
        .filter(u -> !u.getUsername().equals(RecordGroupSharing.ANONYMOUS_USER))
        .collect(Collectors.toSet());
  }

  /** Enum to convert UI choices of target finder policy to a policy implemtation */
  public enum TargetFinderPolicy {
    /** Configures an {@link com.researchspace.service.impl.AllUsersPolicy} */
    ALL,

    /**
     * Configures a {@link
     * com.researchspace.service.impl.StrictPermissionCheckingRecipientFinderPolicy}
     */
    STRICT,

    /** Configures a {@link com.researchspace.service.impl.AllPIsFinderPolicy}. */
    ALL_PIS;
  }

  /**
   * Returns an unordered set of eligible recipients for the combination of message type, record and
   * sender.
   *
   * @param type
   * @param record can be <code>null</code>.
   * @param searchTerm can be <code>null</code>. only targets matching partial search term will be
   *     returned
   * @param sender
   * @return
   */
  Set<User> findPotentialTargetsFor(
      MessageType type, Record record, String searchTerm, User sender);

  /**
   * Return an implementation-specific explanation of why a user may be an invalid target.
   *
   * @return
   */
  String getFailureMessageIfUserInvalidTarget();
}
