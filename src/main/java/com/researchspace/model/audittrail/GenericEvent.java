package com.researchspace.model.audittrail;

import com.researchspace.model.core.Person;

/**
 * Generic event class for simple events that do not need to process specific data values.
 */
public class GenericEvent extends HistoricalEvent {
	private AuditAction action;

	/**
	 * 
	 * @param subject
	 *            the user
	 * @param auditedObject
	 *            the domain object being audited
	 * @param action
	 *            The audit action
	 */
	public GenericEvent(Person subject, Object auditedObject, AuditAction action) {
		this(subject, auditedObject, action, null);
	}

	/**
	 * 
	 * @param subject
	 *            the user
	 * @param auditedObject
	 *            the domain object being audited
	 * @param action
	 *            The audit action
	 * @param description
	 *            An optional, brief, plain-text description of the event, can be null.
	 */
	public GenericEvent(Person subject, Object auditedObject, AuditAction action,
			String description) {
		super(subject, auditedObject, description);
		this.action = action;
	}

	@Override
	public AuditAction getAuditAction() {
		return action;
	}

	@Override
	void accept(AuditEventVisitor visitor) {
		visitor.visit(this);
	}

}
