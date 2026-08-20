package com.researchspace.model.audittrail;

/**
 * Top-level interface for logging /storing audit trails.<br/>
 * This may or may not be transactional depending on the implementation of the
 * logging service.
 */
public interface AuditTrailService {

	/**
	 * Top level client method to interact with the audit trail/analytics module
	 * 
	 * @param auditEvent
	 *            A {@link HistoricalEvent} event class.
	 */
	 void notify(HistoricalEvent auditEvent);
	
	/**
	 * Switches audit trail on/off; the default should be ON.
	 * @param active <code>true</code> to switch on, <code>false</code>to switch off.
	 */
	 void setActive(boolean active);

}
