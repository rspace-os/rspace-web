package com.researchspace.model.field;

public class DateFieldBehaviour {
	
	public static DateFieldForm realOrProxy (IFieldForm ft) {
		if (ft instanceof DateFieldForm) {
			return (DateFieldForm) ft;
		} else if (AbstractField.isAuditingProxy(ft)) {
			return new DateFieldForm();
		} else 
			throw new IllegalStateException();
	}

}
