package com.researchspace.model.record;

import com.researchspace.model.field.Field;

/**
 * Decorator for DocumentInitializationPolicy that will initialise a Document's
 * fields.<br/>
 * This class must be used within a Hibernate session.
 */
public class DocumentFieldInitializationPolicy extends DocumentInitializationPolicy {

	public DocumentFieldInitializationPolicy(DocumentInitializationPolicy policy) {
		super(policy);
	}

	public DocumentFieldInitializationPolicy() {
		super();
	}

	@Override
	protected void doInitialize(BaseRecord baseRecord) {
		if (baseRecord.isStructuredDocument()) {
			StructuredDocument sd = (StructuredDocument) baseRecord;
			for (Field f : sd.getFields()) {
				f.getClass();
			}
		}
	}
}
