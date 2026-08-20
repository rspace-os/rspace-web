package com.researchspace.model.field;

public class UriFieldBehaviour {

	
	public static URIFieldForm realOrProxy (IFieldForm ft) {
		if (ft instanceof URIFieldForm) {
			return (URIFieldForm) ft;
		} else if (AbstractField.isAuditingProxy(ft)) {
			return new URIFieldForm();
		} else 
			throw new IllegalStateException();
	}

}
