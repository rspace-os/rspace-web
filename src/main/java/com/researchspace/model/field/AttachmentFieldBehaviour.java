package com.researchspace.model.field;

public class AttachmentFieldBehaviour {

	
	public static AttachmentFieldForm realOrProxy (IFieldForm ft) {
		if (ft instanceof AttachmentFieldForm) {
			return (AttachmentFieldForm) ft;
		} else if (AbstractField.isAuditingProxy(ft)) {
			return new AttachmentFieldForm();
		} else 
			throw new IllegalStateException();
	}

}
