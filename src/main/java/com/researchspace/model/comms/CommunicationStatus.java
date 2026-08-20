package com.researchspace.model.comms;

/**
 * Tracks a communication's progress from new to completed.
 */
public enum CommunicationStatus {

	/**
	 * The initial state of a request
	 */
	NEW,

	/**
	 * The recipient will not handle the request
	 */
	REJECTED,

	/**
	 * Recipient has indicated they are working on meeting the request
	 */
	ACCEPTED,

	/**
	 * The subject of the request has cancelled
	 */
	CANCELLED,

	/**
	 * Recipient has completed the request
	 */
	COMPLETED,

	/**
	 * Indicates a user has responded to a SimpleMessage; this should only be
	 * used for MessageTypes of SimpleMessage.
	 */
	REPLIED;

	/**
	 * Boolean test for whether a {@link CommunicationStatus} object is in a
	 * terminated state.
	 * 
	 * @return
	 */
	public boolean isTerminated() {
		return equals(CANCELLED) || equals(COMPLETED) || equals(REJECTED);
	}

}
