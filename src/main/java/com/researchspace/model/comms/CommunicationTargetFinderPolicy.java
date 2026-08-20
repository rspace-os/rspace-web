package com.researchspace.model.comms;

import java.util.Set;

import com.researchspace.model.User;
import com.researchspace.model.record.Record;

/**
 * Strategy interface for policies to find possible recipients of a message
 */
public interface CommunicationTargetFinderPolicy {
	/**
	 * Enum to convert UI choices of target finder policy to a policy
	 * implementation
	 */
	enum TargetFinderPolicy {

		ALL,

		STRICT,

		ALL_PIS

	}

	/**
	 * Returns an unordered set of eligible recipients for the combination of
	 * message type, record and sender.
	 * 
	 * @param type
	 * @param record
	 *            can be <code>null</code>.
	 * @param searchTerm
	 *            can be <code>null</code>. only targets matching partial search term will be returned
	 * @param sender
	 * @return
	 */
	Set<User> findPotentialTargetsFor(MessageType type, Record record, String searchTerm, User sender);

	/**
	 * Return an implementation-specific explanation of why a user may be an
	 * invalid target.
	 * 
	 * @return
	 */
	String getFailureMessageIfUserInvalidTarget();

}
