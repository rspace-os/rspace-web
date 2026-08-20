package com.researchspace.model.comms;

public enum NotificationType {

	NOTIFICATION_DOCUMENT_EDITED,

	/**
	 * A document was shared with the subject
	 */
	NOTIFICATION_DOCUMENT_SHARED,

	/**
	 * A document was unshared from the subject
	 */
	NOTIFICATION_DOCUMENT_UNSHARED,

	NOTIFICATION_REQUEST_STATUS_CHANGE,
	/**
	 * A system process completed OK
	 */
	PROCESS_COMPLETED,
	/**
	 * A system process failed
	 */
	PROCESS_FAILED,
	
	NOTIFICATION_DOCUMENT_DELETED,
	
	/**
	 * Archive export completed OK 
	 */
	ARCHIVE_EXPORT_COMPLETED
	
}
