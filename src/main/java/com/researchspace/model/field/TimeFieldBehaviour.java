package com.researchspace.model.field;

public class TimeFieldBehaviour {

	
	public static TimeFieldForm realOrProxy (IFieldForm ft) {
		if (ft instanceof TimeFieldForm) {
			return (TimeFieldForm) ft;
		} else if (AbstractField.isAuditingProxy(ft)) {
			return new TimeFieldForm();
		} else 
			throw new IllegalStateException();
	}

}
