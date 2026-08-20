package com.researchspace.model;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.researchspace.core.util.JacksonUtil;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * DTO with basic Signature information to use in UI. No sensitive content here.
 */
@Data
@EqualsAndHashCode(of="id")
public class SignatureInfo {

	private Long id;
	private String signerFullName;
	private String signDate;
	// Map from username to witnessing date (if pending, null, if declined, 'DECLINED')
	private Map<String, String> witnesses = new LinkedHashMap<>();
	private SignatureStatus status;
	private Set<SignatureHashInfo> hashes = new HashSet<>(); 

	@JsonIgnore
    public String getAsJSON() {
		return JacksonUtil.toJson(this);
	}
}
