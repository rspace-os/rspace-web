package com.researchspace.model.field;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

public class RadioBehaviour {

	
	public static RadioFieldForm realOrProxy (IFieldForm ft) {
		if (ft instanceof RadioFieldForm) {
			return (RadioFieldForm) ft;
		} else if (AbstractField.isAuditingProxy(ft)) {
			return new RadioFieldForm();
		} else 
			throw new IllegalStateException();
	}
	
	public List<String> getAsList(String value) {
		List<String> result = new ArrayList<>();
		if (StringUtils.isBlank(value)) {
			return result;
		}
		String[] options = value.split("&");
		for (String v : options) {
			result.add(v.split("=")[1]);
		}
		return result;
	}

}
