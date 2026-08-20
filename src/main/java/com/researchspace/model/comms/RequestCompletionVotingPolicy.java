package com.researchspace.model.comms;

/**
 * Strategy interface to decide when a Request is complete, based on the
 * responses of the message's recipients.
 */
public interface RequestCompletionVotingPolicy {

	/**
	 * Sets the status of the responder, and /or the original Message, as
	 * COMPLETED, depending on the implementation.
	 * 
	 * @param responder
	 *            represents the authenticated user who is responding to a
	 *            message
	 * 
	 */
	void voteCompleted(CommunicationTarget responder);

}
