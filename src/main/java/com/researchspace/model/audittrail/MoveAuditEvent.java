package com.researchspace.model.audittrail;

import static com.researchspace.core.util.TransformerUtils.toList;

import org.apache.commons.lang3.Validate;

import com.researchspace.model.core.Person;

/**
 * Audits a copy event when an object is copied.
 */
public class MoveAuditEvent extends HistoricalEvent {

	
	protected  Object source;
	protected  Object destination;

	/**
	 * Creates a MoveAuditEvent
	 * @param subject the user performing the copy

	 */
	public MoveAuditEvent(Person subject, Object item, Object source, Object destination) {
		super(subject, item);
		validateArgs(source, destination);
		this.source = source;
		this.destination = destination;
	}

	private void validateArgs(Object source, Object destination) {
		Validate.noNullElements(toList(source, destination), " No null arguments");
	}
	
	public MoveAuditEvent(Person subject, Object item, Object source, Object destination, String desc) {
		super(subject,item,desc);
		validateArgs(source, destination);
		this.source = source;
		this.destination = destination;
	}


	@Override
	public AuditAction getAuditAction() {
		return AuditAction.MOVE;
	}

	protected AuditData getAuditData() {
		AuditData from = getAuditData(source);
		AuditData to = getAuditData(destination);
		AuditData top = getAuditData(getAuditedObject());
		top.put("from", from);
		top.put("to", to);
		return top;
	}


	@Override
	void accept(AuditEventVisitor visitor) {
		visitor.visit(this);
	}

}
