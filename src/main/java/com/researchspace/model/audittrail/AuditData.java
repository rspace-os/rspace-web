package com.researchspace.model.audittrail;

import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.researchspace.core.util.JacksonUtil;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Map ( possibly nested) of key/value pairs storing data particular to an
 * audited object.
 */
@Getter
@EqualsAndHashCode
public class AuditData {

	private Map<String, Object> data = new TreeMap<>();

	public AuditData() {
		super();
	}

	/**
	 * 
	 * @param fieldValue
	 *            should be a primitive type, or serializable to JSON, or an
	 *            AuditData object
	 */
	@JsonAnySetter
	void put(String fieldName, Object fieldValue) {
		data.put(fieldName, fieldValue);
	}

	@JsonAnySetter
	Object get(String fieldName) {
		return data.get(fieldName);
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (String key : data.keySet()) {
			sb.append(key).append("=").append(data.get(key));
			sb.append("&");
		}
		if (sb.length() > 0) {
			sb.deleteCharAt(sb.length() - 1);// remove last '&' character
		}
		return sb.toString();

	}

	public String toJson() {
		return JacksonUtil.toJson(this);
	}

	public static AuditData fromJson(String json) {
		return JacksonUtil.fromJson(json, AuditData.class);
	}

}
