package com.researchspace.model.comms;

import java.util.EnumSet;

import com.researchspace.model.permissions.AbstractEntityPermissionAdapter;
import com.researchspace.model.permissions.MessageTypePermissionsAdapter;
import com.researchspace.model.record.PermissionsAdaptable;

/**
 * Enum for classification of message or request.
 * <p>
 * <h4>Maintenance notes</h4> String representations of these enums are used in
 * the UI, so if renaming these enums be aware to look through the UI code and
 * update strings.
 * <p>
 * Also, these enums are stored in the DB using the their 0-based indexes, so add new
 * Enums to the end of the list.
 */
public enum MessageType implements PermissionsAdaptable {

	/**
	 * A message, with no implied request for the recipient to act.
	 */
	SIMPLE_MESSAGE("Basic message", false, ""),

	/**
	 * A message that is sent to everyone, from an admin
	 */
	GLOBAL_MESSAGE("Message to all users", false, ""),

	/**
	 * Sender requests recipient to view a record
	 */
	REQUEST_RECORD_REVIEW("Review document", new CommunicationStatus[] {

			CommunicationStatus.COMPLETED, CommunicationStatus.REJECTED, CommunicationStatus.ACCEPTED }, false, ""),

	/**
	 * Sender asks PI to join in the creation of a collaboration group
	 */
	REQUEST_EXTERNAL_SHARE("Create a Collaboration Group", new CommunicationStatus[] { CommunicationStatus.COMPLETED, CommunicationStatus.REJECTED }, true, ""),

	/**
	 * Sender asks user to join an existing LabGroup
	 */
	REQUEST_JOIN_LAB_GROUP("Join LabGroup", new CommunicationStatus[] { CommunicationStatus.COMPLETED, CommunicationStatus.REJECTED }, true, ""),

	/**
	 * Sender asks user to create a new Lab Group as new PI. This is a
	 * PublicCloud specific request sent as part of RSPAC-245-use case2
	 */
	REQUEST_CREATE_LAB_GROUP("Create LabGroup as PI", new CommunicationStatus[] { CommunicationStatus.COMPLETED, CommunicationStatus.REJECTED }, true, ""),

	/**
	 * Sender asks user to agree to the sender sharing a record with user.
	 */
	REQUEST_SHARE_RECORD("Share document", new CommunicationStatus[] { CommunicationStatus.COMPLETED, CommunicationStatus.REJECTED }, true, ""),

	/**
	 * Sender asks someone to witness to a signed-document
	 */
	REQUEST_RECORD_WITNESS("Witness document signing", new CommunicationStatus[] { CommunicationStatus.REJECTED }, true, " To proceed, please follow the above link to the document. Then, click on the 'Witness' button."),

	/**
	 * Sender asks PI to join existing collaboration group.
	 */
	REQUEST_JOIN_EXISTING_COLLAB_GROUP("Join existing collaboration Group", new CommunicationStatus[] { CommunicationStatus.COMPLETED, CommunicationStatus.REJECTED }, true, ""),

	/**
	 * Sender asks user to join an existing Project Group.
	 */
	REQUEST_JOIN_PROJECT_GROUP("Join Project Group", new CommunicationStatus[] { CommunicationStatus.COMPLETED, CommunicationStatus.REJECTED }, true, "");

	public static final EnumSet<MessageType> SPECIAL_TYPES = EnumSet.of(REQUEST_CREATE_LAB_GROUP,
			REQUEST_JOIN_LAB_GROUP, REQUEST_SHARE_RECORD, GLOBAL_MESSAGE, REQUEST_JOIN_PROJECT_GROUP);

	public static final EnumSet<MessageType> STANDARD_TYPES = EnumSet.complementOf(SPECIAL_TYPES);

	private String label;
	private boolean isYesNoMessage;
	private String moreInfo;

	/**
	 * Gets general information about this request
	 * 
	 * @return
	 */
	public String getMoreInfo() {
		return moreInfo;
	}

	private CommunicationStatus[] validUpdateStatusesByRecipient = CommunicationStatus.values();

	private MessageType(String label, boolean isYesNoMesg, String moreInfo) {
		this.label = label;
		this.isYesNoMessage = isYesNoMesg;
		this.moreInfo = moreInfo;
	}

	private MessageType(String label, CommunicationStatus[] validStatuses, boolean isYesNoMesg, String moreInfo) {
		this(label, isYesNoMesg, moreInfo);
		this.validUpdateStatusesByRecipient = validStatuses;
	}

	/**
	 * Whether this msg type just requires a yes/no response to accept (true)
	 * and complete the request, or whether there is period of time between
	 * accepted and completed (false).
	 * 
	 * @return
	 */
	public boolean isYesNoMessage() {
		return isYesNoMessage;
	}

	/**
	 * Boolean test for whether this message type is a standard type that should
	 * be included in message/dashboard listings.
	 * 
	 * @return
	 */
	public boolean isStandardType() {
		return STANDARD_TYPES.contains(this);
	}

	/**
	 * Gets the valid statuses for this {@link MessageType} that a recipient can
	 * alter to.
	 * 
	 * @return
	 */
	public CommunicationStatus[] getValidStatusesByRecipient() {
		return validUpdateStatusesByRecipient;
	}

	/**
	 * User-readable representation of the enum.
	 * 
	 * @return
	 */
	public String getLabel() {
		return label;
	}

	@Override
	public AbstractEntityPermissionAdapter getPermissionsAdapter() {
		return new MessageTypePermissionsAdapter(this);
	}

}
