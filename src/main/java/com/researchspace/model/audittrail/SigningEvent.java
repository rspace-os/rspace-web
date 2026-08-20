package com.researchspace.model.audittrail;

import com.researchspace.model.core.Person;

/**
 * Event object for indicating that something has been signed.
 */
public class SigningEvent extends HistoricalEvent {


	/**
	 * 
	 * @param signed The object that was signed
	 * @param signer The user who signed
	 */
	public SigningEvent(Object signed, Person signer) {
		super(signer,signed);
	}

	@Override
	public AuditAction getAuditAction() {
		return AuditAction.SIGN;
	}

	@Override
	void accept(AuditEventVisitor visitor) {
		visitor.visit(this);
	}

}
