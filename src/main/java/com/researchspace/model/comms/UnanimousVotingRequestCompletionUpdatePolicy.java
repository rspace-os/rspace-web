package com.researchspace.model.comms;

/**
 * Sets the request as completed only if <em>ALL</em> recipients of the request
 * complete the request.
 */
public class UnanimousVotingRequestCompletionUpdatePolicy implements RequestCompletionVotingPolicy {

	@Override
	public void voteCompleted(CommunicationTarget responder) {
		if (!responder.getCommunication().isMessageOrRequest()) {
			return;
		}
		MessageOrRequest mor = (MessageOrRequest) responder.getCommunication();

		if (MessageType.GLOBAL_MESSAGE.equals(mor.getMessageType())) {
			return; // RSPAC-1455: iterating over all recipients costs too much, leave with old status
		}
		
		boolean allHaveNowCompleted = true;
		for (CommunicationTarget ct : mor.getRecipients()) {
			if (!ct.getStatus().isTerminated()) {
				allHaveNowCompleted = false;
				break;
			}
		}
		// set status only if all have now completed.
		if (allHaveNowCompleted) {
			mor.setStatus(CommunicationStatus.COMPLETED);
		}

	}

}
